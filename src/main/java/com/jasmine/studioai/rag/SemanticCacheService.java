package com.jasmine.studioai.rag;

import com.jasmine.studioai.model.ChatMessage;
import com.jasmine.studioai.repository.ChatMessageRepository;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.document.Metadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticCacheService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final Map<String, CachedResponse> localCache = new ConcurrentHashMap<>();

    private static final double SIMILARITY_THRESHOLD = 0.92;

    public record CachedResponse(String question, String answer, long timestamp) {}

    public CachedResponse findSimilar(String question) {
        try {
            var emb = embeddingModel.embed(question).content();
            var results = embeddingStore.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(emb)
                    .maxResults(3)
                    .minScore(SIMILARITY_THRESHOLD)
                    .build());

            if (results.matches().isEmpty()) return null;

            var match = results.matches().get(0);
            TextSegment seg = match.embedded();
            if (seg == null || seg.metadata() == null) return null;

            String cachedQuestion = seg.metadata().getString("question");
            String cachedAnswer = seg.text();
            long cachedTs = Long.parseLong(seg.metadata().getString("timestamp"));

            if (cachedAnswer != null && !cachedAnswer.isBlank()) {
                log.info("Semantic cache hit: '{}' -> '{}'", question, cachedQuestion);
                return new CachedResponse(cachedQuestion, cachedAnswer, cachedTs);
            }
        } catch (Exception e) {
            log.warn("Semantic cache lookup failed: {}", e.getMessage());
        }
        return null;
    }

    public void cache(String question, String answer) {
        try {
            var emb = embeddingModel.embed(question).content();
            Metadata meta = new Metadata();
            meta.put("type", "semantic_cache");
            meta.put("question", question);
            meta.put("timestamp", String.valueOf(System.currentTimeMillis()));

            TextSegment seg = TextSegment.from(answer, meta);
            embeddingStore.add(emb, seg);
        } catch (Exception e) {
            localCache.put(question, new CachedResponse(question, answer, System.currentTimeMillis()));
        }
    }
}
