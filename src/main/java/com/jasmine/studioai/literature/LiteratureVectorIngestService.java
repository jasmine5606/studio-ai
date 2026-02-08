package com.jasmine.studioai.literature;

import com.jasmine.studioai.kb.split.TextChunker;
import com.jasmine.studioai.literature.internal.StoredLiteratureItem;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiteratureVectorIngestService {

    public static final String META_SOURCE_TYPE = "sourceType";
    public static final String SOURCE_TYPE_VALUE = "literature";
    public static final String META_USER_ID = "userId";
    public static final String META_LITERATURE_ID = "literatureId";
    public static final String META_CHUNK_INDEX = "chunkIndex";
    public static final String META_TITLE = "title";
    public static final String META_CITATION_ID = "citationId";

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final TextChunker textChunker;
    private final LiteratureFileStore fileStore;

    /**
     * Persist plain text, chunk, embed into vector store with literature-only metadata.
     */
    public StoredLiteratureItem ingestPlainText(String owner,
                                                String title,
                                                String source,
                                                String identifier,
                                                String projectTag,
                                                String fullText) throws IOException {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("owner required");
        String literatureId = Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
        String safeTitle = title == null ? "" : title.trim();

        fileStore.writeExtractedText(owner, literatureId, fullText == null ? "" : fullText);

        List<String> chunks = textChunker.chunk(fullText == null ? "" : fullText);
        List<String> vectorIds = new ArrayList<>();

        int idx = 0;
        for (String chunk : chunks) {
            String citationId = "lit-" + literatureId + "#" + idx;
            Metadata meta = new Metadata();
            meta.put(META_SOURCE_TYPE, SOURCE_TYPE_VALUE);
            meta.put(META_USER_ID, owner);
            meta.put(META_LITERATURE_ID, literatureId);
            meta.put(META_CHUNK_INDEX, String.valueOf(idx));
            meta.put(META_TITLE, safeTitle);
            meta.put(META_CITATION_ID, citationId);

            TextSegment seg = TextSegment.from(chunk, meta);
            try {
                var emb = embeddingModel.embed(seg.text()).content();
                String vid = embeddingStore.add(emb, seg);
                if (vid != null && !vid.isBlank()) vectorIds.add(vid);
            } catch (Exception e) {
                log.warn("Literature vector ingest failed chunk {}: {}", idx, e.getMessage());
            }
            idx++;
        }

        StoredLiteratureItem item = new StoredLiteratureItem();
        item.setLiteratureId(literatureId);
        item.setOwner(owner);
        item.setTitle(safeTitle);
        item.setSource(source);
        item.setIdentifier(identifier == null ? "" : identifier);
        item.setProjectTag(projectTag == null ? "" : projectTag.trim());
        item.setCreatedAt(Instant.now().toString());
        item.setExtractedTextRelativePath("docs/" + literatureId + "/extracted.txt");
        item.setVectorIds(vectorIds);

        fileStore.upsertItem(owner, item);
        return item;
    }
}
