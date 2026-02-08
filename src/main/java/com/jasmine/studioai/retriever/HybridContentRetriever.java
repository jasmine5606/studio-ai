package com.jasmine.studioai.retriever;

import com.jasmine.studioai.kb.KnowledgeBaseRegistry;
import com.jasmine.studioai.kb.RedisBm25Index;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class HybridContentRetriever implements ContentRetriever {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final RedisBm25Index bm25Index;
    private final KnowledgeBaseRegistry registry;

    private final int maxResults = 3;

    public HybridContentRetriever(EmbeddingStore<TextSegment> embeddingStore,
                                  EmbeddingModel embeddingModel,
                                  RedisBm25Index bm25Index,
                                  KnowledgeBaseRegistry registry) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.bm25Index = bm25Index;
        this.registry = registry;
    }

    @Override
    public List<Content> retrieve(Query query) {
        List<ScoredText> scored = retrieveScored(query.text(), maxResults);
        return scored.stream().map(s -> Content.from(s.text())).toList();
    }

    /**
     * Retrieve scored contexts for routing decisions (local/auto).
     * Score is a simple weighted normalized fusion: 0.6 * vector + 0.4 * bm25.
     */
    public List<ScoredText> retrieveScored(String question, int max) {
        return probe(question, max).contexts();
    }

    /**
     * Retrieve contexts plus lightweight signals for "not found" detection.
     */
    public RetrievalProbe probe(String question, int max) {
        int k = Math.max(1, Math.min(20, max));

        // Vector (semantic)
        var queryEmbedding = embeddingModel.embed(question).content();
        var vectorMatches = embeddingStore.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(k)
                        .minScore(0.25)
                        .build())
                .matches();
        double maxVectorScore = vectorMatches.stream()
                .mapToDouble(m -> m.score() == null ? 0.0 : m.score())
                .max()
                .orElse(0.0);

        // BM25 (lexical)
        var bm25Matches = bm25Index.search(question, k * 2);

        log.info("Hybrid retrieve: q='{}', vector={}, bm25={}", question, vectorMatches.size(), bm25Matches.size());

        Map<String, String> textByKey = new HashMap<>();
        Map<String, Double> scoreByKey = new HashMap<>();

        double vecMin = vectorMatches.stream().mapToDouble(m -> m.score() == null ? 0.0 : m.score()).min().orElse(0.0);
        double vecMax = vectorMatches.stream().mapToDouble(m -> m.score() == null ? 0.0 : m.score()).max().orElse(0.0);
        double bmMin = bm25Matches.stream().mapToDouble(RedisBm25Index.ScoredChunk::score).min().orElse(0.0);
        double bmMax = bm25Matches.stream().mapToDouble(RedisBm25Index.ScoredChunk::score).max().orElse(0.0);

        for (var match : vectorMatches) {
            TextSegment seg = match.embedded();
            if (seg == null || seg.text() == null || seg.text().isBlank()) continue;

            String chunkId = seg.metadata() == null ? "" : seg.metadata().getString("chunkId");
            String key = (chunkId == null || chunkId.isBlank()) ? "vec:" + match.embeddingId() : chunkId;

            double raw = match.score() == null ? 0.0 : match.score();
            double norm = (vecMax - vecMin) == 0.0 ? 1.0 : ((raw - vecMin) / (vecMax - vecMin));

            textByKey.put(key, seg.text());
            scoreByKey.merge(key, 0.6 * norm, Double::sum);
        }

        for (var m : bm25Matches) {
            String key = m.chunkId();
            double raw = m.score();
            double norm = (bmMax - bmMin) == 0.0 ? 1.0 : ((raw - bmMin) / (bmMax - bmMin));

            String text = registry.getChunkText(key);
            if (text == null || text.isBlank()) continue;

            textByKey.putIfAbsent(key, text);
            scoreByKey.merge(key, 0.4 * norm, Double::sum);
        }

        List<ScoredText> list = scoreByKey.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(k)
                .map(e -> new ScoredText(e.getValue(), textByKey.getOrDefault(e.getKey(), "")))
                .collect(Collectors.toList());
        return new RetrievalProbe(maxVectorScore, bm25Matches.size(), list);
    }

    public record ScoredText(double score, String text) {
    }

    public record RetrievalProbe(double maxVectorScore, int bm25Count, List<ScoredText> contexts) {
    }
}
