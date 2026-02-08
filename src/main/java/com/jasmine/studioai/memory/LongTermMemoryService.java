package com.jasmine.studioai.memory;

import com.jasmine.studioai.service.SessionMemoryService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.filter.logical.And;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LongTermMemoryService {

    private final SessionMemoryService sessionMemoryService;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public void pin(String userId, String sessionId, String text) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        if (text == null || text.isBlank()) return;

        sessionMemoryService.addPinnedMemory(userId, text);

        Metadata meta = new Metadata();
        meta.put("memoryType", "pin");
        meta.put("userId", userId);
        TextSegment seg = TextSegment.from(text.trim(), meta);

        var emb = embeddingModel.embed(seg.text()).content();
        embeddingStore.add(emb, seg);
    }

    public List<String> listPins(String userId, String sessionId, int limit) {
        // sessionId kept for compatibility; pins are user-scoped
        return sessionMemoryService.getPinnedMemories(userId, Math.max(1, Math.min(200, limit)));
    }

    public List<MemoryHit> recall(String userId, String query, int topK) {
        if (userId == null || userId.isBlank()) return List.of();
        if (query == null || query.isBlank()) return List.of();

        var qEmb = embeddingModel.embed(query).content();
        var filter = new And(
                MetadataFilterBuilder.metadataKey("memoryType").isEqualTo("pin"),
                MetadataFilterBuilder.metadataKey("userId").isEqualTo(userId)
        );

        var req = EmbeddingSearchRequest.builder()
                .queryEmbedding(qEmb)
                .maxResults(Math.max(1, Math.min(20, topK)))
                .minScore(0.0)
                .filter(filter)
                .build();

        var result = embeddingStore.search(req);
        if (result == null || result.matches() == null) return List.of();

        return result.matches().stream()
                .map(m -> new MemoryHit(m.score() == null ? 0.0 : m.score(), m.embedded() == null ? "" : m.embedded().text()))
                .toList();
    }

    public record MemoryHit(double score, String text) {
    }
}
