package com.jasmine.studioai.kb;

import com.jasmine.studioai.kb.dto.KbIngestResponse;
import com.jasmine.studioai.kb.extract.TextExtractors;
import com.jasmine.studioai.kb.split.TextChunker;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseIngestService {

    private final KnowledgeBaseRegistry registry;
    private final TextChunker chunker;
    private final RedisBm25Index bm25Index;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    /**
     * Upload -> extract text -> chunk -> (1) embed+upsert to vector store (2) index to Redis BM25.
     */
    public KbIngestResponse ingest(MultipartFile file, String source, String tags) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }

        String docId = newDocId();
        String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String contentType = file.getContentType() == null ? "" : file.getContentType();

        try {
            Path stored = registry.saveUploadedFile(docId, originalName, file.getBytes());

            String text = TextExtractors.extract(stored, contentType);
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("Extracted text is empty, unsupported file type or content");
            }

            return ingestTextInternal(docId, originalName, contentType, text, source, tags, stored);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to ingest file: " + e.getMessage(), e);
        }
    }

    /**
     * Ingest plain text as a KB document (used by external sync jobs like Bilibili).
     */
    public KbIngestResponse ingestText(String filename, String text, String source, String tags) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text is required");
        }
        String docId = newDocId();
        String name = (filename == null || filename.isBlank()) ? "note.txt" : filename.trim();
        try {
            Path stored = registry.saveUploadedFile(docId, name, text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return ingestTextInternal(docId, name, "text/plain", text, source, tags, stored);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to ingest text: " + e.getMessage(), e);
        }
    }

    public void delete(String docId) {
        var chunkIds = registry.getDocChunks(docId);
        for (String chunkId : chunkIds) {
            // Remove from vector store (best-effort)
            try {
                String vectorId = registry.getChunkVectorId(chunkId);
                if (vectorId != null && !vectorId.isBlank()) {
                    embeddingStore.remove(vectorId);
                }
            } catch (Exception e) {
                log.warn("Vector deletion failed (chunkId={}): {}", chunkId, e.getMessage());
            }

            bm25Index.deleteChunk(chunkId);
            registry.deleteChunkText(chunkId);
            registry.deleteChunkVectorId(chunkId);
        }
        registry.deleteDoc(docId);
        registry.deleteUploadedFile(docId);
    }

    private KbIngestResponse ingestTextInternal(String docId,
                                                String filename,
                                                String contentType,
                                                String text,
                                                String source,
                                                String tags,
                                                Path stored) {
        var meta = new KbMetadata(docId, filename, contentType, nullToEmpty(source), nullToEmpty(tags), Instant.now().toString());
        registry.registerDoc(meta);

        List<String> chunks = chunker.chunk(text);
        List<TextSegment> segments = new ArrayList<>(chunks.size());
        List<String> chunkIds = new ArrayList<>(chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            String chunkId = docId + ":" + i;
            chunkIds.add(chunkId);
            String chunkText = chunks.get(i);

            registry.saveChunkText(chunkId, chunkText);
            bm25Index.indexChunk(chunkId, chunkText, meta.toMetadata());

            Metadata lcMeta = new Metadata();
            lcMeta.put("docId", docId);
            lcMeta.put("chunkId", chunkId);
            lcMeta.put("filename", filename);
            lcMeta.put("contentType", contentType);
            if (source != null && !source.isBlank()) lcMeta.put("source", source);
            if (tags != null && !tags.isBlank()) lcMeta.put("tags", tags);
            segments.add(TextSegment.from(chunkText, lcMeta));
        }

        // Vector store ingestion (best-effort, keeps chunk metadata)
        try {
            for (TextSegment seg : segments) {
                var emb = embeddingModel.embed(seg.text()).content();
                String vectorId = embeddingStore.add(emb, seg);
                String chunkId = seg.metadata() == null ? "" : seg.metadata().getString("chunkId");
                if (chunkId != null && !chunkId.isBlank()) {
                    registry.saveChunkVectorId(chunkId, vectorId);
                }
            }
        } catch (Exception e) {
            log.warn("Vector ingestion failed (docId={}): {}", docId, e.getMessage());
        }

        registry.linkDocChunks(docId, chunkIds);

        KbIngestResponse response = new KbIngestResponse();
        response.setDocId(docId);
        response.setFilename(filename);
        response.setChunks(chunks.size());
        response.setStoredPath(stored == null ? "" : stored.toString());
        return response;
    }

    private static String newDocId() {
        return Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
