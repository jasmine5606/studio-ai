package com.jasmine.studioai.literature;

import com.jasmine.studioai.auth.UserContext;
import com.jasmine.studioai.literature.dto.LiteratureAnalyzeRequest;
import com.jasmine.studioai.literature.dto.LiteratureDocSummaryResponse;
import com.jasmine.studioai.literature.dto.LiteratureGeneratedResponse;
import com.jasmine.studioai.literature.dto.LiteratureIdentifierRequest;
import com.jasmine.studioai.literature.dto.LiteratureSearchHit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Literature workbench: separate vector domain ({@code sourceType=literature}) + file registry per user.
 */
@RestController
@RequestMapping("/api/literature")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class LiteratureController {

    private final LiteratureIngestFacade ingestFacade;
    private final LiteratureCatalogService catalogService;
    private final LiteratureRetrievalService retrievalService;
    private final LiteratureAnalysisService analysisService;

    @PostMapping(value = "/ingest/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LiteratureDocSummaryResponse ingestPdf(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String projectTag,
            @RequestParam(required = false) String title
    ) throws IOException {
        return ingestFacade.ingestPdf(UserContext.username(), file, projectTag, title);
    }

    @PostMapping("/ingest/identifier")
    public LiteratureDocSummaryResponse ingestIdentifier(@RequestBody LiteratureIdentifierRequest body) throws IOException {
        if (body == null || body.getIdentifier() == null || body.getIdentifier().isBlank()) {
            throw new IllegalArgumentException("identifier is required");
        }
        return ingestFacade.ingestIdentifier(UserContext.username(), body.getIdentifier(), body.getProjectTag());
    }

    @GetMapping("/docs")
    public List<LiteratureDocSummaryResponse> listDocs() throws IOException {
        return catalogService.list(UserContext.username());
    }

    @GetMapping("/docs/{literatureId}")
    public LiteratureDocSummaryResponse getDoc(@PathVariable String literatureId) throws IOException {
        return catalogService.get(UserContext.username(), literatureId);
    }

    @DeleteMapping("/docs/{literatureId}")
    public void deleteDoc(@PathVariable String literatureId) throws IOException {
        catalogService.delete(UserContext.username(), literatureId);
    }

    @GetMapping("/search")
    public List<LiteratureSearchHit> search(
            @RequestParam("q") String q,
            @RequestParam(required = false) String literatureId,
            @RequestParam(defaultValue = "8") int topK
    ) {
        return retrievalService.search(UserContext.username(), q, literatureId, topK);
    }

    @PostMapping("/analyze")
    public LiteratureGeneratedResponse analyze(@RequestBody LiteratureAnalyzeRequest req) throws IOException {
        return new LiteratureGeneratedResponse(analysisService.analyze(req));
    }

    @PostMapping("/generate/meeting")
    public LiteratureGeneratedResponse meeting(@RequestBody LiteratureAnalyzeRequest req) throws IOException {
        return new LiteratureGeneratedResponse(analysisService.meetingNotes(req));
    }

    @PostMapping("/generate/related-work")
    public LiteratureGeneratedResponse relatedWork(@RequestBody LiteratureAnalyzeRequest req) throws IOException {
        return new LiteratureGeneratedResponse(analysisService.relatedWorkDraft(req));
    }
}
