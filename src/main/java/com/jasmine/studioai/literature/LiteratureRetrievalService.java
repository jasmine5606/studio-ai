package com.jasmine.studioai.literature;

import com.jasmine.studioai.literature.dto.LiteratureSearchHit;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.filter.logical.And;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LiteratureRetrievalService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Value("${lab.literature.search.min-score:0.15}")
    private double minScore;

    public List<LiteratureSearchHit> search(String owner, String query, String literatureId, int topK) {
        if (owner == null || owner.isBlank()) return List.of();
        if (query == null || query.isBlank()) return List.of();
        int k = Math.max(1, Math.min(30, topK));

        var qEmb = embeddingModel.embed(query).content();

        var typeUser = new And(
                MetadataFilterBuilder.metadataKey(LiteratureVectorIngestService.META_SOURCE_TYPE)
                        .isEqualTo(LiteratureVectorIngestService.SOURCE_TYPE_VALUE),
                MetadataFilterBuilder.metadataKey(LiteratureVectorIngestService.META_USER_ID).isEqualTo(owner)
        );

        var reqBuilder = EmbeddingSearchRequest.builder()
                .queryEmbedding(qEmb)
                .maxResults(k)
                .minScore(Math.max(0.0, Math.min(1.0, minScore)))
                .filter(literatureId == null || literatureId.isBlank()
                        ? typeUser
                        : new And(
                        typeUser,
                        MetadataFilterBuilder.metadataKey(LiteratureVectorIngestService.META_LITERATURE_ID)
                                .isEqualTo(literatureId.trim())
                ));

        var result = embeddingStore.search(reqBuilder.build());
        if (result == null || result.matches() == null) return List.of();

        List<LiteratureSearchHit> out = new ArrayList<>();
        for (var m : result.matches()) {
            TextSegment seg = m.embedded();
            if (seg == null) continue;
            LiteratureSearchHit h = new LiteratureSearchHit();
            h.setCitationId(meta(seg, LiteratureVectorIngestService.META_CITATION_ID));
            h.setLiteratureId(meta(seg, LiteratureVectorIngestService.META_LITERATURE_ID));
            h.setChunkIndex(parseInt(meta(seg, LiteratureVectorIngestService.META_CHUNK_INDEX), -1));
            h.setScore(m.score() == null ? 0.0 : m.score());
            h.setText(seg.text());
            h.setTitle(meta(seg, LiteratureVectorIngestService.META_TITLE));
            out.add(h);
        }
        return out;
    }

    private static String meta(TextSegment seg, String key) {
        if (seg.metadata() == null) return "";
        String v = seg.metadata().getString(key);
        return v == null ? "" : v;
    }

    private static int parseInt(String s, int dflt) {
        if (s == null || s.isBlank()) return dflt;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }
}
