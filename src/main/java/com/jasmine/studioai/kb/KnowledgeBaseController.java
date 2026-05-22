package com.jasmine.studioai.kb;

import com.jasmine.studioai.kb.dto.KbDocInfo;
import com.jasmine.studioai.kb.dto.KbIngestResponse;
import com.jasmine.studioai.kb.dto.KbSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/kb")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseIngestService ingestService;
    private final KnowledgeBaseRegistry registry;
    private final KnowledgeBaseSearchService searchService;
    private final KnowledgeBaseMaintenanceService maintenanceService;

    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KbIngestResponse upload(@RequestPart("file") MultipartFile file,
                                   @RequestParam(required = false) String source,
                                   @RequestParam(required = false) String tags) {
        return ingestService.ingest(file, source, tags);
    }

    @GetMapping("/files")
    public List<KbDocInfo> list() {
        return registry.listDocs();
    }

    @DeleteMapping("/files/{docId}")
    public void delete(@PathVariable String docId) {
        ingestService.delete(docId);
    }

    @GetMapping("/search")
    public KbSearchResponse search(@RequestParam("q") String query,
                                   @RequestParam(defaultValue = "hybrid") String mode,
                                   @RequestParam(defaultValue = "5") int topK) {
        return searchService.search(query, mode, topK);
    }

    @PostMapping("/reindex")
    public ReindexResponse reindex() {
        var r = maintenanceService.reindexBm25();
        return new ReindexResponse(r.docs(), r.chunks());
    }

    public record ReindexResponse(int docs, int chunks) {
    }
}
