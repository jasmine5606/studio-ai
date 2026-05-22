package com.jasmine.studioai.codereview;

import com.jasmine.studioai.codereview.dto.*;
import com.jasmine.studioai.github.GitHubApiClient;
import com.jasmine.studioai.prompt.PromptTemplateService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CodeReviewService {

    private final ChatLanguageModel chatModel;
    private final PromptTemplateService promptTemplateService;
    private final GitHubApiClient gitHubApiClient;

    public CodeReviewResponse review(CodeReviewRequest request) {
        String reviewId = newReviewId();
        List<CodeReviewResponse.FileReview> fileReviews = new ArrayList<>();

        if (request.getFiles() == null || request.getFiles().isEmpty()) {
            CodeReviewResponse response = new CodeReviewResponse();
            response.setReviewId(reviewId);
            response.setProfile(request.getProfile());
            response.setFileReviews(List.of());
            return response;
        }

        for (CodeFile file : request.getFiles()) {
            String language = firstNonBlank(request.getLanguage(), file.getLanguage(), "text");
            String prompt = promptTemplateService.render(
                    "prompts/code_review/review.txt",
                    request.getProfile(),
                    PromptTemplateService.vars()
                            .put("language", language)
                            .put("path", nullToEmpty(file.getPath()))
                            .put("context", nullToEmpty(request.getContext()))
                            .put("code", nullToEmpty(file.getContent()))
                            .build()
            );

            String review = chatModel.chat(prompt);
            CodeReviewResponse.FileReview fr = new CodeReviewResponse.FileReview();
            fr.setPath(file.getPath());
            fr.setLanguage(language);
            fr.setReview(review);
            fileReviews.add(fr);
        }

        CodeReviewResponse response = new CodeReviewResponse();
        response.setReviewId(reviewId);
        response.setProfile(request.getProfile());
        response.setFileReviews(fileReviews);
        return response;
    }

    public UnitTestResponse generateUnitTest(UnitTestRequest request) {
        String reviewId = newReviewId();
        String language = firstNonBlank(request.getLanguage(), "text");

        String prompt = promptTemplateService.render(
                "prompts/code_review/unit_test.txt",
                request.getProfile(),
                PromptTemplateService.vars()
                        .put("language", language)
                        .put("framework", nullToEmpty(request.getFramework()))
                        .put("code", nullToEmpty(request.getCode()))
                        .build()
        );

        String unitTest = chatModel.chat(prompt);
        UnitTestResponse response = new UnitTestResponse();
        response.setReviewId(reviewId);
        response.setProfile(request.getProfile());
        response.setUnitTestCode(unitTest);
        return response;
    }

    public PrReviewResponse reviewPullRequest(PrReviewRequest request) {
        String reviewId = newReviewId();

        var pr = gitHubApiClient.getPullRequest(request.getOwner(), request.getRepo(), request.getPrNumber());
        var files = gitHubApiClient.listPullRequestFiles(request.getOwner(), request.getRepo(), request.getPrNumber());
        String ref = firstNonBlank(request.getRef(), pr.headSha(), "main");

        List<PrReviewResponse.FileChangeReview> fileReviews = new ArrayList<>();
        for (var f : files) {
            String language = detectLanguageFromFilename(f.filename());
            String fileContent = "";
            if (!"removed".equalsIgnoreCase(f.status())) {
                fileContent = gitHubApiClient.getFileContent(request.getOwner(), request.getRepo(), f.filename(), ref);
            }

            String prompt = promptTemplateService.render(
                    "prompts/code_review/pr_review.txt",
                    request.getProfile(),
                    PromptTemplateService.vars()
                            .put("owner", request.getOwner())
                            .put("repo", request.getRepo())
                            .put("prNumber", String.valueOf(request.getPrNumber()))
                            .put("filename", f.filename())
                            .put("status", f.status())
                            .put("additions", String.valueOf(f.additions()))
                            .put("deletions", String.valueOf(f.deletions()))
                            .put("patch", nullToEmpty(f.patch()))
                            .put("language", language)
                            .put("context", nullToEmpty(request.getContext()))
                            .put("fileContent", nullToEmpty(fileContent))
                            .build()
            );

            String review = chatModel.chat(prompt);
            PrReviewResponse.FileChangeReview r = new PrReviewResponse.FileChangeReview();
            r.setFilename(f.filename());
            r.setStatus(f.status());
            r.setAdditions(f.additions());
            r.setDeletions(f.deletions());
            r.setPatch(f.patch());
            r.setReview(review);
            fileReviews.add(r);
        }

        String summaryPrompt = promptTemplateService.render(
                "prompts/code_review/pr_summary.txt",
                request.getProfile(),
                PromptTemplateService.vars()
                        .put("owner", request.getOwner())
                        .put("repo", request.getRepo())
                        .put("prNumber", String.valueOf(request.getPrNumber()))
                        .put("title", pr.title())
                        .put("body", nullToEmpty(pr.body()))
                        .put("context", nullToEmpty(request.getContext()))
                        .put("fileReviews", fileReviews.stream().map(fr -> fr.getFilename() + "\n" + fr.getReview()).reduce("", (a, b) -> a + "\n\n" + b))
                        .build()
        );
        String summary = chatModel.chat(summaryPrompt);

        PrReviewResponse response = new PrReviewResponse();
        response.setReviewId(reviewId);
        response.setProfile(request.getProfile());
        response.setOwner(request.getOwner());
        response.setRepo(request.getRepo());
        response.setPrNumber(request.getPrNumber());
        response.setFileReviews(fileReviews);
        response.setSummary(summary);
        return response;
    }

    private static String newReviewId() {
        return Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.trim().isEmpty()) return c.trim();
        }
        return "";
    }

    private static String detectLanguageFromFilename(String filename) {
        if (filename == null) return "text";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".kt")) return "kotlin";
        if (lower.endsWith(".js")) return "javascript";
        if (lower.endsWith(".ts")) return "typescript";
        if (lower.endsWith(".py")) return "python";
        if (lower.endsWith(".go")) return "go";
        if (lower.endsWith(".rb")) return "ruby";
        if (lower.endsWith(".cs")) return "csharp";
        if (lower.endsWith(".cpp") || lower.endsWith(".cc") || lower.endsWith(".cxx")) return "cpp";
        if (lower.endsWith(".c")) return "c";
        if (lower.endsWith(".md")) return "markdown";
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "yaml";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".xml")) return "xml";
        return "text";
    }
}
