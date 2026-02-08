package com.jasmine.studioai.kb;

import com.jasmine.studioai.kb.dto.KbSearchResponse;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseSearchService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final KnowledgeBaseRegistry registry;
    private final RedisBm25Index bm25Index;

    @Value("${kb.search.vector-min-score:0.25}")
    private double vectorMinScore;

    @Value("${kb.search.hybrid-min-score:0.18}")
    private double hybridMinScore;

    public KbSearchResponse search(String query, String mode, int topK) {
        String m = (mode == null || mode.isBlank()) ? "hybrid" : mode.trim().toLowerCase();
        int k = Math.max(1, Math.min(50, topK));

        List<KbSearchResponse.Item> items = switch (m) {
            case "vector" -> vector(query, k);
            case "bm25" -> bm25(query, k);
            default -> hybrid(query, k);
        };

        KbSearchResponse resp = new KbSearchResponse();
        resp.setQuery(query);
        resp.setMode(m);
        resp.setItems(items);
        return resp;
    }

    private List<KbSearchResponse.Item> vector(String query, int topK) {
        var queryEmbedding = embeddingModel.embed(query).content();
        var matches = embeddingStore.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(topK)
                        .minScore(Math.max(0.0, Math.min(1.0, vectorMinScore)))
                        .build())
                .matches();
        List<KbSearchResponse.Item> out = new ArrayList<>();
        for (var match : matches) {
            TextSegment seg = match.embedded();
            String chunkId = seg.metadata() == null ? "" : seg.metadata().getString("chunkId");
            String docId = seg.metadata() == null ? "" : seg.metadata().getString("docId");

            KbSearchResponse.Item it = new KbSearchResponse.Item();
            it.setChunkId(chunkId.isBlank() ? match.embeddingId() : chunkId);
            it.setDocId(docId);
            it.setScore(match.score() == null ? 0.0 : match.score());
            it.setText(seg.text());
            out.add(it);
        }
        return out;
    }

    private List<KbSearchResponse.Item> bm25(String query, int topK) {
        List<RedisBm25Index.ScoredChunk> matches = bm25Index.search(query, topK);
        List<KbSearchResponse.Item> out = new ArrayList<>();
        for (var m : matches) {
            String chunkId = m.chunkId();
            KbSearchResponse.Item it = new KbSearchResponse.Item();
            it.setChunkId(chunkId);
            it.setDocId(bm25Index.getChunkMetaString(chunkId, "docId"));
            it.setScore(m.score());
            it.setText(registry.getChunkText(chunkId));
            out.add(it);
        }
        return out;
    }

    private List<KbSearchResponse.Item> hybrid(String query, int topK) {
        List<KbSearchResponse.Item> vec = vector(query, Math.max(8, topK * 2));
        List<KbSearchResponse.Item> bm = bm25(query, Math.max(15, topK * 3));

        Map<String, KbSearchResponse.Item> merged = new HashMap<>();
        Map<String, Double> vecNorm = normalizeVector(vec, vectorMinScore);
        Map<String, Double> bmNorm = normalizeByMax(bm);

        for (KbSearchResponse.Item it : vec) {
            String key = safeChunkKey(it);
            merged.putIfAbsent(key, cloneItem(it));
        }
        for (KbSearchResponse.Item it : bm) {
            String key = safeChunkKey(it);
            merged.putIfAbsent(key, cloneItem(it));
        }

        for (Map.Entry<String, KbSearchResponse.Item> e : merged.entrySet()) {
            String chunkKey = e.getKey();
            double v = vecNorm.getOrDefault(chunkKey, 0.0);
            double b = bmNorm.getOrDefault(chunkKey, 0.0);
            // weights: favor semantic search slightly
            e.getValue().setScore(0.6 * v + 0.4 * b);
            if (e.getValue().getText() == null || e.getValue().getText().isBlank()) {
                e.getValue().setText(registry.getChunkText(chunkKey));
            }
            if (e.getValue().getDocId() == null || e.getValue().getDocId().isBlank()) {
                e.getValue().setDocId(bm25Index.getChunkMetaString(chunkKey, "docId"));
            }
            e.getValue().setChunkId(chunkKey);
        }

        return merged.values().stream()
                .filter(it -> it.getScore() >= hybridMinScore)
                .sorted((a, b1) -> Double.compare(b1.getScore(), a.getScore()))
                .limit(topK)
                .toList();
    }

    private static String safeChunkKey(KbSearchResponse.Item it) {
        if (it == null) return "";
        String c = it.getChunkId();
        if (c != null && !c.isBlank()) return c;
        return "";
    }

    private static Map<String, Double> normalizeVector(List<KbSearchResponse.Item> items, double minScore) {
        double ms = Math.max(0.0, Math.min(0.95, minScore));
        Map<String, Double> norm = new HashMap<>();
        for (KbSearchResponse.Item it : items) {
            if (it == null) continue;
            String key = safeChunkKey(it);
            double raw = it.getScore();
            double v = (raw - ms) / (1.0 - ms);
            if (v < 0) v = 0;
            if (v > 1) v = 1;
            norm.put(key, v);
        }
        return norm;
    }

    private static Map<String, Double> normalizeByMax(List<KbSearchResponse.Item> items) {
        Map<String, Double> raw = new HashMap<>();
        double max = 0.0;
        for (KbSearchResponse.Item it : items) {
            if (it == null) continue;
            String key = safeChunkKey(it);
            double s = it.getScore();
            raw.put(key, s);
            max = Math.max(max, s);
        }
        Map<String, Double> norm = new HashMap<>();
        for (Map.Entry<String, Double> e : raw.entrySet()) {
            double v = max <= 0.0 ? 0.0 : (e.getValue() / max);
            if (v < 0) v = 0;
            if (v > 1) v = 1;
            norm.put(e.getKey(), v);
        }
        return norm;
    }

    private static KbSearchResponse.Item cloneItem(KbSearchResponse.Item src) {
        KbSearchResponse.Item it = new KbSearchResponse.Item();
        it.setChunkId(src.getChunkId());
        it.setDocId(src.getDocId());
        it.setScore(src.getScore());
        it.setText(src.getText());
        return it;
    }
}
