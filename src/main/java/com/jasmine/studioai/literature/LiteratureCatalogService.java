package com.jasmine.studioai.literature;

import com.jasmine.studioai.literature.dto.LiteratureDocSummaryResponse;
import com.jasmine.studioai.literature.internal.LiteratureRegistryFile;
import com.jasmine.studioai.literature.internal.StoredLiteratureItem;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LiteratureCatalogService {

    private final LiteratureFileStore fileStore;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public List<LiteratureDocSummaryResponse> list(String owner) throws IOException {
        LiteratureRegistryFile reg = fileStore.loadRegistry(owner);
        List<LiteratureDocSummaryResponse> out = new ArrayList<>();
        for (StoredLiteratureItem item : reg.getItems()) {
            out.add(toSummary(owner, item));
        }
        out.sort((a, b) -> String.valueOf(b.getCreatedAt()).compareTo(String.valueOf(a.getCreatedAt())));
        return out;
    }

    public LiteratureDocSummaryResponse get(String owner, String literatureId) throws IOException {
        StoredLiteratureItem item = fileStore.findItem(owner, literatureId);
        if (item == null) throw new IllegalArgumentException("文献不存在");
        return toSummary(owner, item);
    }

    public void delete(String owner, String literatureId) throws IOException {
        StoredLiteratureItem item = fileStore.findItem(owner, literatureId);
        if (item == null) throw new IllegalArgumentException("文献不存在");
        if (item.getVectorIds() != null) {
            for (String vid : item.getVectorIds()) {
                if (vid == null || vid.isBlank()) continue;
                try {
                    embeddingStore.remove(vid);
                } catch (Exception ignored) {
                }
            }
        }
        fileStore.removeItem(owner, literatureId);
        fileStore.deleteDocTree(owner, literatureId);
    }

    private LiteratureDocSummaryResponse toSummary(String owner, StoredLiteratureItem item) throws IOException {
        String text = fileStore.readExtractedText(owner, item.getLiteratureId());
        LiteratureDocSummaryResponse o = new LiteratureDocSummaryResponse();
        o.setLiteratureId(item.getLiteratureId());
        o.setTitle(item.getTitle());
        o.setSource(item.getSource());
        o.setIdentifier(item.getIdentifier());
        o.setProjectTag(item.getProjectTag());
        o.setCreatedAt(item.getCreatedAt());
        int n = item.getVectorIds() == null ? 0 : item.getVectorIds().size();
        o.setChunkCount(n);
        String preview = text.trim();
        if (preview.length() > 400) preview = preview.substring(0, 400) + "…";
        o.setTextPreview(preview);
        o.setChunkCitationIds(java.util.stream.IntStream.range(0, n)
                .mapToObj(i -> "lit-" + item.getLiteratureId() + "#" + i)
                .collect(Collectors.toList()));
        return o;
    }
}
