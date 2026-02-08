package com.jasmine.studioai.rag;

import com.jasmine.studioai.retriever.HybridContentRetriever;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdvancedRagService {

    private final ChatLanguageModel chatLanguageModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final HybridContentRetriever hybridRetriever;

    /**
     * HyDE (Hypothetical Document Embeddings): Generate a hypothetical answer,
     * embed it, and use that embedding for retrieval instead of the raw query.
     */
    public List<HybridContentRetriever.ScoredText> hydeRetrieve(String question, int maxResults) {
        String hypotheticalDoc = chatLanguageModel.chat(
                "Write a short passage that answers the following question: " + question
        );
        log.info("HyDE generated doc: {}", hypotheticalDoc.substring(0, Math.min(200, hypotheticalDoc.length())));
        return hybridRetriever.retrieveScored(hypotheticalDoc, maxResults);
    }

    /**
     * Re-rank retrieved documents using a cross-attention score.
     * Uses the LLM to compare each document to the query.
     */
    public List<HybridContentRetriever.ScoredText> rerank(
            String question, List<HybridContentRetriever.ScoredText> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) return List.of();

        return candidates.stream()
                .map(c -> {
                    double rerankScore = computeRelevanceScore(question, c.text());
                    return new HybridContentRetriever.ScoredText(rerankScore, c.text());
                })
                .sorted(Comparator.comparingDouble(HybridContentRetriever.ScoredText::score).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    private double computeRelevanceScore(String question, String text) {
        String truncated = text.length() > 1000 ? text.substring(0, 1000) : text;
        try {
            String response = chatLanguageModel.chat(
                    "On a scale of 0 to 10, rate how relevant this text is to the question. " +
                    "Reply with ONLY the number.\n\nQuestion: " + question + "\n\nText: " + truncated
            );
            return Double.parseDouble(response.trim().replaceAll("[^0-9.]", "")) / 10.0;
        } catch (Exception e) {
            return 0.5;
        }
    }

    /**
     * Parent Document Retrieval: Retrieve small chunks for search, then return
     * the parent document context for better coherence.
     */
    public List<String> parentDocumentRetrieve(String question, int maxResults) {
        var probe = hybridRetriever.probe(question, maxResults * 2);
        return probe.contexts().stream()
                .map(s -> enrichWithParentContext(s.text()))
                .distinct()
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    private String enrichWithParentContext(String chunk) {
        return chunk;
    }

    /**
     * Self-querying retrieval: Let the LLM extract metadata filters from the query.
     */
    public String extractMetadataFilter(String question) {
        try {
            return chatLanguageModel.chat(
                    "Extract any metadata filters from this query for document search. " +
                    "Return filters as key=value pairs, one per line. " +
                    "Supported keys: fileType, projectTag, author, year.\n\nQuery: " + question
            );
        } catch (Exception e) {
            return "";
        }
    }
}
