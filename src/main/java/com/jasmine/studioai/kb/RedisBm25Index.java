package com.jasmine.studioai.kb;

import dev.langchain4j.data.document.Metadata;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class RedisBm25Index {

    private final StringRedisTemplate redis;
    private final Tokenizer tokenizer = new Tokenizer();

    private static final String PREFIX = "kb:bm25:v2:";
    private static final String N_KEY = PREFIX + "N";
    private static final String TOTAL_LEN_KEY = PREFIX + "totalLen";
    private static final String DOCS_SET = PREFIX + "docs";

    private final Map<String, Map<String, Integer>> localPostings = new ConcurrentHashMap<>();
    private final Map<String, Integer> localDocLen = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> localDocMeta = new ConcurrentHashMap<>();
    private final Set<String> localDocs = ConcurrentHashMap.newKeySet();
    private volatile long localN = 0;
    private volatile long localTotalLen = 0;

    public void indexChunk(String chunkId, String text, Metadata metadata) {
        if (chunkId == null || chunkId.isBlank()) return;
        if (text == null) text = "";

        List<String> tokens = tokenizer.tokenize(text);
        if (tokens.isEmpty()) {
            try {
                redis.opsForHash().put(docMetaKey(chunkId), "docLen", "0");
            } catch (Exception e) {
                localDocLen.put(chunkId, 0);
            }
            return;
        }

        Map<String, Integer> tf = new HashMap<>();
        for (String t : tokens) {
            tf.merge(t, 1, Integer::sum);
        }

        int docLen = tokens.size();

        try {
            redis.opsForHash().put(docMetaKey(chunkId), "docLen", String.valueOf(docLen));
            if (metadata != null) {
                Map<String, Object> m = metadata.toMap();
                for (Map.Entry<String, Object> e : m.entrySet()) {
                    if (e.getValue() != null) {
                        redis.opsForHash().put(docMetaKey(chunkId), "m:" + e.getKey(), String.valueOf(e.getValue()));
                    }
                }
            }

            redis.opsForSet().add(docTermsKey(chunkId), tf.keySet().toArray(new String[0]));
            redis.opsForSet().add(DOCS_SET, chunkId);
            for (Map.Entry<String, Integer> e : tf.entrySet()) {
                redis.opsForHash().put(termKey(e.getKey()), chunkId, String.valueOf(e.getValue()));
            }
            redis.opsForValue().increment(N_KEY, 1);
            redis.opsForValue().increment(TOTAL_LEN_KEY, docLen);
        } catch (Exception e) {
            localDocs.add(chunkId);
            localDocLen.put(chunkId, docLen);
            Map<String, String> meta = localDocMeta.computeIfAbsent(chunkId, k -> new ConcurrentHashMap<>());
            meta.put("docLen", String.valueOf(docLen));
            if (metadata != null) {
                Map<String, Object> m = metadata.toMap();
                for (Map.Entry<String, Object> entry : m.entrySet()) {
                    if (entry.getValue() != null) {
                        meta.put("m:" + entry.getKey(), String.valueOf(entry.getValue()));
                    }
                }
            }
            for (Map.Entry<String, Integer> postingEntry : tf.entrySet()) {
                localPostings.computeIfAbsent(postingEntry.getKey(), k -> new ConcurrentHashMap<>()).put(chunkId, postingEntry.getValue());
            }
            localN += 1;
            localTotalLen += docLen;
        }
    }

    public void deleteChunk(String chunkId) {
        if (chunkId == null || chunkId.isBlank()) return;
        try {
            Set<String> terms = redis.opsForSet().members(docTermsKey(chunkId));
            if (terms != null) {
                for (String term : terms) {
                    redis.opsForHash().delete(termKey(term), chunkId);
                }
            }
            redis.delete(docTermsKey(chunkId));
            redis.delete(docMetaKey(chunkId));
            redis.opsForSet().remove(DOCS_SET, chunkId);
        } catch (Exception e) {
            localDocs.remove(chunkId);
            localDocLen.remove(chunkId);
            localDocMeta.remove(chunkId);
            for (Map<String, Integer> postings : localPostings.values()) {
                postings.remove(chunkId);
            }
        }
    }

    public void clearAll() {
        try {
            Set<String> docs = redis.opsForSet().members(DOCS_SET);
            if (docs != null) {
                for (String doc : docs) {
                    deleteChunk(doc);
                }
            }
            redis.delete(DOCS_SET);
            redis.delete(N_KEY);
            redis.delete(TOTAL_LEN_KEY);
        } catch (Exception e) {
            localDocs.clear();
            localPostings.clear();
            localDocLen.clear();
            localDocMeta.clear();
            localN = 0;
            localTotalLen = 0;
        }
    }

    public List<ScoredChunk> search(String query, int topK) {
        List<String> qTokens = tokenizer.tokenize(query);
        if (qTokens.isEmpty()) return List.of();

        try {
            return searchRedis(qTokens, topK);
        } catch (Exception e) {
            return searchLocal(qTokens, topK);
        }
    }

    public String getChunkMetaString(String chunkId, String key) {
        try {
            Object v = redis.opsForHash().get(docMetaKey(chunkId), "m:" + key);
            return v == null ? "" : String.valueOf(v);
        } catch (Exception e) {
            return localDocMeta.getOrDefault(chunkId, Map.of()).getOrDefault("m:" + key, "");
        }
    }

    private List<ScoredChunk> searchRedis(List<String> qTokens, int topK) {
        long N = parseLong(redis.opsForValue().get(N_KEY));
        long totalLen = parseLong(redis.opsForValue().get(TOTAL_LEN_KEY));
        double avgdl = N == 0 ? 0.0 : (totalLen * 1.0 / N);

        double k1 = 1.2;
        double b = 0.75;
        Map<String, Double> scores = new HashMap<>();

        for (String term : qTokens) {
            Map<Object, Object> postings = redis.opsForHash().entries(termKey(term));
            if (postings == null || postings.isEmpty()) continue;

            int df = postings.size();
            double idf = Math.log((N - df + 0.5) / (df + 0.5) + 1.0);
            for (Map.Entry<Object, Object> p : postings.entrySet()) {
                String docId = String.valueOf(p.getKey());
                int tf = parseInt(String.valueOf(p.getValue()));
                int dl = parseInt(String.valueOf(redis.opsForHash().get(docMetaKey(docId), "docLen")));
                double denom = tf + k1 * (1.0 - b + b * (avgdl == 0.0 ? 1.0 : (dl / avgdl)));
                double termScore = idf * (tf * (k1 + 1.0)) / (denom == 0.0 ? 1.0 : denom);
                scores.merge(docId, termScore, Double::sum);
            }
        }

        return scores.entrySet().stream()
                .sorted((a, b1) -> Double.compare(b1.getValue(), a.getValue()))
                .limit(Math.max(1, topK))
                .map(e -> new ScoredChunk(e.getKey(), e.getValue()))
                .toList();
    }

    private List<ScoredChunk> searchLocal(List<String> qTokens, int topK) {
        long N = Math.max(0, localN);
        long totalLen = Math.max(0, localTotalLen);
        double avgdl = N == 0 ? 0.0 : (totalLen * 1.0 / N);
        double k1 = 1.2;
        double b = 0.75;

        Map<String, Double> scores = new HashMap<>();
        for (String term : qTokens) {
            Map<String, Integer> postings = localPostings.getOrDefault(term, Map.of());
            if (postings.isEmpty()) continue;
            int df = postings.size();
            double idf = Math.log((N - df + 0.5) / (df + 0.5) + 1.0);
            for (Map.Entry<String, Integer> p : postings.entrySet()) {
                String docId = p.getKey();
                int tf = p.getValue();
                int dl = localDocLen.getOrDefault(docId, 0);
                double denom = tf + k1 * (1.0 - b + b * (avgdl == 0.0 ? 1.0 : (dl / avgdl)));
                double termScore = idf * (tf * (k1 + 1.0)) / (denom == 0.0 ? 1.0 : denom);
                scores.merge(docId, termScore, Double::sum);
            }
        }

        return scores.entrySet().stream()
                .sorted((a, b1) -> Double.compare(b1.getValue(), a.getValue()))
                .limit(Math.max(1, topK))
                .map(e -> new ScoredChunk(e.getKey(), e.getValue()))
                .toList();
    }

    private static String termKey(String term) {
        return "kb:bm25:term:" + term;
    }

    private static String docMetaKey(String docId) {
        return "kb:bm25:doc:" + docId;
    }

    private static String docTermsKey(String docId) {
        return "kb:bm25:doc:" + docId + ":terms";
    }

    private static long parseLong(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int parseInt(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public record ScoredChunk(String chunkId, double score) {
    }
}
