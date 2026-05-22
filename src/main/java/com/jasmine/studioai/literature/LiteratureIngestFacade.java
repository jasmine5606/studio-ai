package com.jasmine.studioai.literature;

import com.jasmine.studioai.kb.extract.TextExtractors;
import com.jasmine.studioai.literature.dto.LiteratureDocSummaryResponse;
import com.jasmine.studioai.literature.internal.StoredLiteratureItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * PDF / arXiv / DOI → plain text → vector domain {@code sourceType=literature}.
 */
@Service
@RequiredArgsConstructor
public class LiteratureIngestFacade {

    private final LiteratureVectorIngestService vectorIngestService;
    private final LiteratureFileStore fileStore;
    private final ArxivAndCrossrefFetcher fetcher;

    public LiteratureDocSummaryResponse ingestPdf(String owner, MultipartFile file, String projectTag, String titleOverride) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("file is required");
        String original = file.getOriginalFilename() == null ? "paper.pdf" : file.getOriginalFilename();
        String ct = file.getContentType() == null ? "" : file.getContentType();

        String literatureIdPlaceholder = "pending-" + System.currentTimeMillis();
        Path tmp = Files.createTempFile("lit-upload-", "-" + safeName(original));
        try {
            Files.write(tmp, file.getBytes());
            String text = TextExtractors.extract(tmp, ct);
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("PDF 文本抽取为空（可能为扫描版），请改用 OCR 流程或提供 arXiv/DOI");
            }
            String title = titleOverride != null && !titleOverride.isBlank() ? titleOverride.trim() : guessTitleFromFilename(original);
            StoredLiteratureItem item = vectorIngestService.ingestPlainText(owner, title, "pdf", original, projectTag, text);
            fileStore.writeUploadedBinary(owner, item.getLiteratureId(), original, file.getBytes());
            return toSummary(item, text);
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
        }
    }

    public LiteratureDocSummaryResponse ingestIdentifier(String owner, String identifier, String projectTag) throws IOException {
        ArxivAndCrossrefFetcher.FetchResult r = fetcher.fetchByIdentifier(identifier);
        StoredLiteratureItem item = vectorIngestService.ingestPlainText(
                owner,
                r.title(),
                r.source(),
                r.identifier(),
                projectTag,
                r.fullText()
        );
        return toSummary(item, r.fullText());
    }

    private LiteratureDocSummaryResponse toSummary(StoredLiteratureItem item, String text) {
        LiteratureDocSummaryResponse o = new LiteratureDocSummaryResponse();
        o.setLiteratureId(item.getLiteratureId());
        o.setTitle(item.getTitle());
        o.setSource(item.getSource());
        o.setIdentifier(item.getIdentifier());
        o.setProjectTag(item.getProjectTag());
        o.setCreatedAt(item.getCreatedAt());
        o.setChunkCount(item.getVectorIds() == null ? 0 : item.getVectorIds().size());
        String preview = text == null ? "" : text.trim();
        if (preview.length() > 400) preview = preview.substring(0, 400) + "…";
        o.setTextPreview(preview);
        List<String> cites = new java.util.ArrayList<>();
        for (int i = 0; i < o.getChunkCount(); i++) {
            cites.add("lit-" + item.getLiteratureId() + "#" + i);
        }
        o.setChunkCitationIds(cites);
        return o;
    }

    private static String guessTitleFromFilename(String name) {
        if (name == null) return "Uploaded PDF";
        String n = name.replace('_', ' ');
        if (n.toLowerCase().endsWith(".pdf")) n = n.substring(0, n.length() - 4);
        return n.isBlank() ? "Uploaded PDF" : n;
    }

    private static String safeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
