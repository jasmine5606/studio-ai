package com.jasmine.studioai.bilibili;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auth store for Bilibili QR login sessions.
 * Uses Redis when available; falls back to in-memory map (best-effort).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BilibiliAuthStore {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    private final Map<String, AuthSession> local = new ConcurrentHashMap<>();
    private static final Duration TTL = Duration.ofHours(12);

    public void put(String bindId, AuthSession session) {
        if (bindId == null || bindId.isBlank() || session == null) {
            log.warn("BilibiliAuthStore.put: rejected null param (bindId={}, session={})", bindId, session);
            return;
        }
        local.put(bindId, session);
        try {
            redis.opsForValue().set(key(bindId), objectMapper.writeValueAsString(session), TTL);
        } catch (Exception e) {
            log.warn("BilibiliAuthStore.put: Redis write failed for bindId={}: {}", bindId, e.getMessage());
        }
    }

    public AuthSession get(String bindId) {
        if (bindId == null || bindId.isBlank()) return null;
        try {
            String json = redis.opsForValue().get(key(bindId));
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, AuthSession.class);
            }
        } catch (Exception ignored) {
        }
        return local.get(bindId);
    }

    public void clear(String bindId) {
        if (bindId == null || bindId.isBlank()) return;
        local.remove(bindId);
        try {
            redis.delete(key(bindId));
        } catch (Exception ignored) {
        }
    }

    private static String key(String bindId) {
        return "bili:auth:" + bindId;
    }

    @Data
    public static class AuthSession {
        private String bindId;
        private String qrcodeKey;
        private String qrcodeUrl;
        private String cookie;
        private String createdAt = Instant.now().toString();
        private boolean bound;
        private String message;
    }
}

