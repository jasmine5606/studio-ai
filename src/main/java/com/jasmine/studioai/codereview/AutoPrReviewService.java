package com.jasmine.studioai.codereview;

import com.jasmine.studioai.codereview.dto.AutoPrReviewRequest;
import com.jasmine.studioai.codereview.dto.AutoPrReviewResponse;
import com.jasmine.studioai.github.GitHubApiClient;
import com.jasmine.studioai.prompt.PromptTemplateService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AutoPrReviewService {

    private final ChatLanguageModel chatModel;
    private final PromptTemplateService promptTemplateService;
    private final GitHubApiClient gitHubApiClient;

    @Value("${mcp.enabled:false}")
    private boolean mcpEnabled;

    @Value("${github.auto-review.require-oauth:false}")
    private boolean requireOauthToken;

    public AutoPrReviewResponse review(AutoPrReviewRequest request) {
        validateRequest(request);

        int maxFiles = request.getMaxFiles() == null ? 60 : Math.max(1, Math.min(200, request.getMaxFiles()));
        int chunkChars = request.getChunkChars() == null ? 3500 : Math.max(1200, Math.min(8000, request.getChunkChars()));
        String token = normalizeToken(request.getOauthToken());

        var pr = gitHubApiClient.getPullRequest(request.getOwner(), request.getRepo(), request.getPrNumber(), token);
        var files = gitHubApiClient.listPullRequestFiles(request.getOwner(), request.getRepo(), request.getPrNumber(), token);

        List<GitHubApiClient.PullRequestFile> filtered = applySafetyFilter(files, Boolean.TRUE.equals(request.getSafeMode()), maxFiles);
        List<AutoPrReviewResponse.FileResult> fileResults = new ArrayList<>();
        int totalChunks = 0;

        for (GitHubApiClient.PullRequestFile f : filtered) {
            String language = detectLanguageFromFilename(f.filename());
            String fileContent = "";
            if (!"removed".equalsIgnoreCase(f.status())) {
                fileContent = gitHubApiClient.getFileContent(request.getOwner(), request.getRepo(), f.filename(), pr.headSha(), token);
            }

            List<String> chunks = splitPatch(f.patch(), chunkChars);
            if (chunks.isEmpty()) chunks = List.of("");
            totalChunks += chunks.size();

            StringBuilder merged = new StringBuilder();
            for (int i = 0; i < chunks.size(); i++) {
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
                                .put("patch", chunks.get(i))
                                .put("language", language)
                                .put("context", nullToEmpty(request.getContext()))
                                .put("fileContent", nullToEmpty(fileContent))
                                .build()
                );
                String part = chatModel.chat(prompt);
                merged.append("Chunk ").append(i + 1).append("/").append(chunks.size()).append("\n").append(part).append("\n\n");
            }

            AutoPrReviewResponse.FileResult fr = new AutoPrReviewResponse.FileResult();
            fr.setFilename(f.filename());
            fr.setStatus(f.status());
            fr.setChunkCount(chunks.size());
            fr.setReview(merged.toString().trim());
            fileResults.add(fr);
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
                        .put("fileReviews", fileResults.stream().map(r -> r.getFilename() + "\n" + r.getReview()).reduce("", (a, b) -> a + "\n\n" + b))
                        .build()
        );
        String summary = chatModel.chat(summaryPrompt);

        AutoPrReviewResponse response = new AutoPrReviewResponse();
        response.setReviewId(newReviewId());
        response.setOwner(request.getOwner());
        response.setRepo(request.getRepo());
        response.setPrNumber(request.getPrNumber());
        response.setMcpEnabled(mcpEnabled);
        response.setAnalyzedFiles(fileResults.size());
        response.setAnalyzedChunks(totalChunks);
        response.setSummary(summary);
        response.setFiles(fileResults);
        return response;
    }

    private void validateRequest(AutoPrReviewRequest request) {
        if (request == null) throw new IllegalArgumentException("request is required");
        if (request.getOwner() == null || request.getOwner().isBlank()) throw new IllegalArgumentException("owner is required");
        if (request.getRepo() == null || request.getRepo().isBlank()) throw new IllegalArgumentException("repo is required");
        if (request.getPrNumber() <= 0) throw new IllegalArgumentException("prNumber must be > 0");
        if (requireOauthToken && (request.getOauthToken() == null || request.getOauthToken().isBlank())) {
            throw new IllegalArgumentException("oauthToken is required by policy");
        }
    }

    private static List<GitHubApiClient.PullRequestFile> applySafetyFilter(List<GitHubApiClient.PullRequestFile> files, boolean safeMode, int maxFiles) {
        if (files == null || files.isEmpty()) return List.of();
        if (!safeMode) return files.stream().limit(maxFiles).toList();
        Set<String> denySuffix = new HashSet<>(List.of(".pem", ".key", ".p12", ".pfx", ".crt", ".cer", ".kdbx"));
        List<GitHubApiClient.PullRequestFile> out = new ArrayList<>();
        for (GitHubApiClient.PullRequestFile f : files) {
            if (out.size() >= maxFiles) break;
            String lower = f.filename() == null ? "" : f.filename().toLowerCase();
            boolean denied = denySuffix.stream().anyMatch(lower::endsWith);
            if (!denied) out.add(f);
        }
        return out;
    }

    private static List<String> splitPatch(String patch, int chunkChars) {
        String text = patch == null ? "" : patch.trim();
        if (text.isBlank()) return List.of();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + chunkChars);
            int newline = text.lastIndexOf('\n', end);
            if (newline <= start + 200) newline = end;
            chunks.add(text.substring(start, newline).trim());
            start = newline;
            while (start < text.length() && (text.charAt(start) == '\n' || text.charAt(start) == '\r')) start++;
        }
        return chunks;
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

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String normalizeToken(String token) {
        if (token == null) return null;
        String t = token.trim();
        if (t.toLowerCase().startsWith("bearer ")) return t.substring(7).trim();
        return t;
    }

    private static String newReviewId() {
        return Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
    }
}
