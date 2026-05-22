package com.jasmine.studioai.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Session memory (per user):
 * - short-term: Redis List + sliding window + summary
 * - pins: Redis List (manual memories)
 *
 * When Redis is unavailable, falls back to in-memory storage so the app remains usable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionMemoryService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final int WINDOW_SIZE = 10;
    private static final int MAX_BUFFER_SIZE = 50;
    private static final int SUMMARIZE_TRIGGER_SIZE = 30;
    private static final Duration SESSION_TTL = Duration.ofHours(24);

    private final Map<String, Deque<Message>> localMessages = new ConcurrentHashMap<>();
    private final Map<String, String> localSummary = new ConcurrentHashMap<>();
    private final Map<String, String> localTitles = new ConcurrentHashMap<>();
    private final Map<String, Long> localLastActive = new ConcurrentHashMap<>();
    private final Map<String, Deque<String>> localPins = new ConcurrentHashMap<>();

    public void addMessage(String userId, String sessionId, String role, String content) {
        String u = normUser(userId);
        String s = normSession(sessionId);
        Message message = new Message(role, content, System.currentTimeMillis());

        try {
            ensureMetaRedis(u, s, message, role, content);
            redisTemplate.opsForZSet().add(sessionIndexKey(u), s, message.getTimestamp());
            String key = sessionMsgsKey(u, s);
            redisTemplate.opsForList().leftPush(key, message);
            redisTemplate.opsForList().trim(key, 0, MAX_BUFFER_SIZE - 1);
            redisTemplate.expire(key, SESSION_TTL.getSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            ensureMetaLocal(u, s, message, role, content);
            addLocalMessage(u, s, message);
        }
    }

    public List<Message> getRecentMessages(String userId, String sessionId, int limit) {
        String u = normUser(userId);
        String s = normSession(sessionId);
        int n = Math.max(1, Math.min(200, limit));

        try {
            List<Object> objects = redisTemplate.opsForList().range(sessionMsgsKey(u, s), 0, -1);
            if (objects == null || objects.isEmpty()) return List.of();
            return objects.stream()
                    .map(obj -> (Message) obj)
                    .limit(n)
                    .collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
                        java.util.Collections.reverse(list);
                        return list;
                    }));
        } catch (Exception e) {
            return localRecentMessages(u, s, n);
        }
    }

    public List<Message> getWindowMessages(String userId, String sessionId) {
        return getRecentMessages(userId, sessionId, WINDOW_SIZE);
    }

    public long size(String userId, String sessionId) {
        String u = normUser(userId);
        String s = normSession(sessionId);
        try {
            Long size = redisTemplate.opsForList().size(sessionMsgsKey(u, s));
            return size == null ? 0L : size;
        } catch (Exception e) {
            Deque<Message> deque = localMessages.get(localSessionKey(u, s));
            return deque == null ? 0L : deque.size();
        }
    }

    public List<Message> getMessagesBeyondWindow(String userId, String sessionId) {
        String u = normUser(userId);
        String s = normSession(sessionId);
        try {
            List<Object> objects = redisTemplate.opsForList().range(sessionMsgsKey(u, s), WINDOW_SIZE, -1);
            if (objects == null || objects.isEmpty()) return List.of();
            List<Message> list = objects.stream().map(obj -> (Message) obj).collect(Collectors.toList());
            java.util.Collections.reverse(list);
            return list;
        } catch (Exception e) {
            return localMessagesBeyondWindow(u, s);
        }
    }

    public void trimToWindow(String userId, String sessionId) {
        String u = normUser(userId);
        String s = normSession(sessionId);
        try {
            redisTemplate.opsForList().trim(sessionMsgsKey(u, s), 0, WINDOW_SIZE - 1);
        } catch (Exception e) {
            Deque<Message> deque = localMessages.get(localSessionKey(u, s));
            if (deque == null) return;
            while (deque.size() > WINDOW_SIZE) {
                deque.removeLast();
            }
        }
    }

    public boolean shouldSummarize(String userId, String sessionId) {
        return size(userId, sessionId) >= SUMMARIZE_TRIGGER_SIZE;
    }

    public String getSummary(String userId, String sessionId) {
        String u = normUser(userId);
        String s = normSession(sessionId);
        try {
            Object v = redisTemplate.opsForValue().get(sessionSummaryKey(u, s));
            return v == null ? "" : String.valueOf(v);
        } catch (Exception e) {
            return localSummary.getOrDefault(localSessionKey(u, s), "");
        }
    }

    public void setSummary(String userId, String sessionId, String summary) {
        String u = normUser(userId);
        String s = normSession(sessionId);
        String value = summary == null ? "" : summary;
        try {
            redisTemplate.opsForValue().set(
                    sessionSummaryKey(u, s),
                    value,
                    SESSION_TTL.getSeconds(),
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            localSummary.put(localSessionKey(u, s), value);
        }
    }

    public void addPinnedMemory(String userId, String sessionId, String text) {
        addPinnedMemory(userId, text);
    }

    public List<String> getPinnedMemories(String userId, String sessionId, int limit) {
        return getPinnedMemories(userId, limit);
    }

    public void addPinnedMemory(String userId, String text) {
        if (text == null || text.isBlank()) return;
        String u = normUser(userId);
        String value = text.trim();
        try {
            String key = userPinsKey(u);
            redisTemplate.opsForList().leftPush(key, value);
            redisTemplate.opsForList().trim(key, 0, 199);
            redisTemplate.expire(key, SESSION_TTL.getSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            Deque<String> deque = localPins.computeIfAbsent(u, k -> new ArrayDeque<>());
            deque.addFirst(value);
            while (deque.size() > 200) {
                deque.removeLast();
            }
        }
    }

    public List<String> getPinnedMemories(String userId, int limit) {
        String u = normUser(userId);
        int n = Math.max(1, Math.min(200, limit));
        try {
            List<Object> objects = redisTemplate.opsForList().range(userPinsKey(u), 0, n - 1);
            if (objects == null || objects.isEmpty()) return List.of();
            List<String> list = objects.stream().map(String::valueOf).collect(Collectors.toList());
            java.util.Collections.reverse(list);
            return list;
        } catch (Exception e) {
            Deque<String> deque = localPins.get(u);
            if (deque == null || deque.isEmpty()) return List.of();
            return deque.stream()
                    .limit(n)
                    .collect(Collectors.collectingAndThen(Collectors.toCollection(ArrayList::new), list -> {
                        java.util.Collections.reverse(list);
                        return list;
                    }));
        }
    }

    public List<String> listSessions(String userId, int limit) {
        String u = normUser(userId);
        int n = Math.max(1, Math.min(100, limit));
        try {
            var set = redisTemplate.opsForZSet().reverseRange(sessionIndexKey(u), 0, n - 1);
            if (set == null || set.isEmpty()) return List.of();
            return set.stream().map(String::valueOf).toList();
        } catch (Exception e) {
            return localSessionIndex(u).entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(n)
                    .map(Map.Entry::getKey)
                    .toList();
        }
    }

    public long getLastActive(String userId, String sessionId) {
        String u = normUser(userId);
        String s = normSession(sessionId);
        try {
            Double score = redisTemplate.opsForZSet().score(sessionIndexKey(u), s);
            return score == null ? 0L : score.longValue();
        } catch (Exception e) {
            return localLastActive.getOrDefault(localSessionKey(u, s), 0L);
        }
    }

    public String getTitle(String userId, String sessionId) {
        String u = normUser(userId);
        String s = normSession(sessionId);
        try {
            Object v = redisTemplate.opsForHash().get(sessionMetaKey(u, s), "title");
            return v == null ? "" : String.valueOf(v);
        } catch (Exception e) {
            return localTitles.getOrDefault(localSessionKey(u, s), "");
        }
    }

    public void setTitle(String userId, String sessionId, String title) {
        String u = normUser(userId);
        String s = normSession(sessionId);
        String value = title == null ? "" : title.trim();
        try {
            redisTemplate.opsForHash().put(sessionMetaKey(u, s), "title", value);
            redisTemplate.expire(sessionMetaKey(u, s), SESSION_TTL.getSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            localTitles.put(localSessionKey(u, s), value);
        }
    }

    private void ensureMetaRedis(String userId, String sessionId, Message message, String role, String content) {
        String metaKey = sessionMetaKey(userId, sessionId);
        Boolean exists = redisTemplate.hasKey(metaKey);
        if (exists == null || !exists) {
            redisTemplate.opsForHash().putAll(metaKey, Map.of(
                    "createdAt", String.valueOf(message.getTimestamp()),
                    "title", ""
            ));
            redisTemplate.expire(metaKey, SESSION_TTL.getSeconds(), TimeUnit.SECONDS);
        }

        if (Objects.equals("user", role) && content != null && !content.isBlank()) {
            Object cur = redisTemplate.opsForHash().get(metaKey, "title");
            String curTitle = cur == null ? "" : String.valueOf(cur);
            if (curTitle.isBlank()) {
                String title = toTitle(content);
                redisTemplate.opsForHash().put(metaKey, "title", title);
                redisTemplate.expire(metaKey, SESSION_TTL.getSeconds(), TimeUnit.SECONDS);
            }
        }
    }

    private void ensureMetaLocal(String userId, String sessionId, Message message, String role, String content) {
        String key = localSessionKey(userId, sessionId);
        localLastActive.put(key, message.getTimestamp());
        localMessages.computeIfAbsent(key, k -> new ArrayDeque<>());
        localTitles.putIfAbsent(key, "");
        localSummary.putIfAbsent(key, "");
        if (Objects.equals("user", role) && content != null && !content.isBlank() && localTitles.get(key).isBlank()) {
            localTitles.put(key, toTitle(content));
        }
    }

    private void addLocalMessage(String userId, String sessionId, Message message) {
        String key = localSessionKey(userId, sessionId);
        Deque<Message> deque = localMessages.computeIfAbsent(key, k -> new ArrayDeque<>());
        deque.addFirst(message);
        while (deque.size() > MAX_BUFFER_SIZE) {
            deque.removeLast();
        }
        localLastActive.put(key, message.getTimestamp());
        localTitles.putIfAbsent(key, "");
        localSummary.putIfAbsent(key, "");
    }

    private List<Message> localRecentMessages(String userId, String sessionId, int limit) {
        Deque<Message> deque = localMessages.get(localSessionKey(userId, sessionId));
        if (deque == null || deque.isEmpty()) return List.of();
        return deque.stream()
                .limit(limit)
                .collect(Collectors.collectingAndThen(Collectors.toCollection(ArrayList::new), list -> {
                    java.util.Collections.reverse(list);
                    return list;
                }));
    }

    private List<Message> localMessagesBeyondWindow(String userId, String sessionId) {
        Deque<Message> deque = localMessages.get(localSessionKey(userId, sessionId));
        if (deque == null || deque.size() <= WINDOW_SIZE) return List.of();
        return deque.stream()
                .skip(WINDOW_SIZE)
                .collect(Collectors.collectingAndThen(Collectors.toCollection(ArrayList::new), list -> {
                    java.util.Collections.reverse(list);
                    return list;
                }));
    }

    private Map<String, Long> localSessionIndex(String userId) {
        Map<String, Long> out = new LinkedHashMap<>();
        String prefix = localUserPrefix(userId);
        for (Map.Entry<String, Long> entry : localLastActive.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                out.put(entry.getKey().substring(prefix.length()), entry.getValue());
            }
        }
        return out;
    }

    private static String toTitle(String text) {
        String s = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if (s.isBlank()) return "新对话";
        int max = 18;
        int cpCount = s.codePointCount(0, s.length());
        if (cpCount <= max) return s;
        int end = s.offsetByCodePoints(0, max);
        return s.substring(0, end) + "…";
    }

    private static String normUser(String userId) {
        String u = userId == null ? "" : userId.trim();
        return u.isBlank() ? "default" : u;
    }

    private static String normSession(String sessionId) {
        String s = sessionId == null ? "" : sessionId.trim();
        return s.isBlank() ? "default" : s;
    }

    private static String localUserPrefix(String userId) {
        return "u:" + normUser(userId) + ":s:";
    }

    private static String localSessionKey(String userId, String sessionId) {
        return localUserPrefix(userId) + normSession(sessionId);
    }

    private static String sessionIndexKey(String userId) {
        return "u:" + userId + ":sessions";
    }

    private static String sessionMsgsKey(String userId, String sessionId) {
        return "u:" + userId + ":s:" + sessionId + ":msgs";
    }

    private static String sessionSummaryKey(String userId, String sessionId) {
        return "u:" + userId + ":s:" + sessionId + ":summary";
    }

    private static String userPinsKey(String userId) {
        return "u:" + userId + ":pins";
    }

    private static String sessionMetaKey(String userId, String sessionId) {
        return "u:" + userId + ":s:" + sessionId + ":meta";
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Message {
        private String role;
        private String content;
        private long timestamp;
    }
}
