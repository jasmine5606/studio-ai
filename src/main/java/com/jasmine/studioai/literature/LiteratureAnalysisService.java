package com.jasmine.studioai.literature;

import com.jasmine.studioai.auth.UserContext;
import com.jasmine.studioai.literature.dto.LiteratureAnalyzeRequest;
import com.jasmine.studioai.literature.dto.LiteratureSearchHit;
import com.jasmine.studioai.prompt.PromptTemplateService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LiteratureAnalysisService {

    private static final int MAX_FULLTEXT_CHARS = 28000;

    private final ChatLanguageModel chatLanguageModel;
    private final PromptTemplateService promptTemplateService;
    private final LiteratureFileStore fileStore;
    private final LiteratureCatalogService catalogService;
    private final LiteratureRetrievalService retrievalService;

    public String analyze(LiteratureAnalyzeRequest req) throws IOException {
        validate(req);
        var doc = catalogService.get(UserContext.username(), req.getLiteratureId());
        String text = truncate(fileStore.readExtractedText(UserContext.username(), req.getLiteratureId()));
        String prompt = promptTemplateService.render(
                "prompts/literature/analyze.txt",
                req.getProfile(),
                PromptTemplateService.vars()
                        .put("title", doc.getTitle())
                        .put("projectContext", nullToEmpty(req.getProjectContext()))
                        .put("fullText", text)
                        .build()
        );
        return chatLanguageModel.chat(prompt);
    }

    public String meetingNotes(LiteratureAnalyzeRequest req) throws IOException {
        validate(req);
        var doc = catalogService.get(UserContext.username(), req.getLiteratureId());
        String text = truncate(fileStore.readExtractedText(UserContext.username(), req.getLiteratureId()));
        String prompt = promptTemplateService.render(
                "prompts/literature/meeting_notes.txt",
                req.getProfile(),
                PromptTemplateService.vars()
                        .put("title", doc.getTitle())
                        .put("projectContext", nullToEmpty(req.getProjectContext()))
                        .put("fullText", text)
                        .build()
        );
        return chatLanguageModel.chat(prompt);
    }

    public String relatedWorkDraft(LiteratureAnalyzeRequest req) throws IOException {
        validate(req);
        var doc = catalogService.get(UserContext.username(), req.getLiteratureId());
        String ctx = nullToEmpty(req.getProjectContext());
        String rq = ctx.isBlank()
                ? "contributions methods limitations experimental setup"
                : ctx + " contributions methods limitations comparison";

        List<LiteratureSearchHit> hits = retrievalService.search(
                UserContext.username(),
                rq,
                req.getLiteratureId(),
                10
        );
        String evidence = hits.stream()
                .map(h -> "[" + h.getCitationId() + "] (score=" + String.format("%.3f", h.getScore()) + ")\n" + h.getText())
                .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = promptTemplateService.render(
                "prompts/literature/related_work.txt",
                req.getProfile(),
                PromptTemplateService.vars()
                        .put("title", doc.getTitle())
                        .put("projectContext", ctx)
                        .put("retrievedChunks", evidence.isBlank() ? "（检索无结果，请仅基于标题与课题语境谨慎撰写）" : evidence)
                        .build()
        );
        return chatLanguageModel.chat(prompt);
    }

    private static void validate(LiteratureAnalyzeRequest req) {
        if (req == null || req.getLiteratureId() == null || req.getLiteratureId().isBlank()) {
            throw new IllegalArgumentException("literatureId is required");
        }
    }

    private static String truncate(String text) {
        if (text == null) return "";
        if (text.length() <= MAX_FULLTEXT_CHARS) return text;
        return text.substring(0, MAX_FULLTEXT_CHARS) + "\n\n[... 后文已截断，完整文本已分块入库，请用 /api/literature/search 检索 ...]";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
