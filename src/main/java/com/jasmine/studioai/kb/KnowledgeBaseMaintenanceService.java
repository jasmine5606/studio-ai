package com.jasmine.studioai.kb;

import com.jasmine.studioai.kb.dto.KbDocInfo;
import dev.langchain4j.data.document.Metadata;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseMaintenanceService {

    private final KnowledgeBaseRegistry registry;
    private final RedisBm25Index bm25Index;

    public ReindexResult reindexBm25() {
        bm25Index.clearAll();

        int docs = 0;
        int chunks = 0;

        List<KbDocInfo> docInfos = registry.listDocs();
        for (KbDocInfo info : docInfos) {
            docs++;
            String docId = info.getDocId();
            Metadata meta = new Metadata();
            meta.put("docId", docId);
            if (info.getFilename() != null && !info.getFilename().isBlank()) meta.put("filename", info.getFilename());
            if (info.getSource() != null && !info.getSource().isBlank()) meta.put("source", info.getSource());
            if (info.getTags() != null && !info.getTags().isBlank()) meta.put("tags", info.getTags());

            for (String chunkId : registry.getDocChunks(docId)) {
                String text = registry.getChunkText(chunkId);
                bm25Index.indexChunk(chunkId, text, meta);
                chunks++;
            }
        }

        return new ReindexResult(docs, chunks);
    }

    public record ReindexResult(int docs, int chunks) {
    }
}

