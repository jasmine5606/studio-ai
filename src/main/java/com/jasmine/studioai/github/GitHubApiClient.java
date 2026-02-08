package com.jasmine.studioai.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
public class GitHubApiClient {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${github.token:}")
    private String githubToken;

    @Value("${github.default-owner:}")
    private String defaultOwner;

    @Value("${github.default-repo:}")
    private String defaultRepo;

    public GitHubApiClient(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public PullRequestInfo getPullRequest(String owner, String repo, int prNumber) {
        return getPullRequest(owner, repo, prNumber, null);
    }

    public PullRequestInfo getPullRequest(String owner, String repo, int prNumber, String tokenOverride) {
        String finalOwner = pick(owner, defaultOwner);
        String finalRepo = pick(repo, defaultRepo);
        String url = String.format("https://api.github.com/repos/%s/%s/pulls/%d", finalOwner, finalRepo, prNumber);
        JsonNode root = getJson(url, tokenOverride);
        String title = text(root, "title");
        String body = text(root, "body");
        String headSha = root.path("head").path("sha").asText("");
        return new PullRequestInfo(finalOwner, finalRepo, prNumber, title, body, headSha);
    }

    public List<PullRequestFile> listPullRequestFiles(String owner, String repo, int prNumber) {
        return listPullRequestFiles(owner, repo, prNumber, null);
    }

    public List<PullRequestFile> listPullRequestFiles(String owner, String repo, int prNumber, String tokenOverride) {
        String finalOwner = pick(owner, defaultOwner);
        String finalRepo = pick(repo, defaultRepo);

        // GitHub API pagination: 100 per page
        List<PullRequestFile> all = new ArrayList<>();
        for (int page = 1; page <= 10; page++) {
            String url = String.format("https://api.github.com/repos/%s/%s/pulls/%d/files?per_page=100&page=%d",
                    finalOwner, finalRepo, prNumber, page);
            JsonNode arr = getJson(url, tokenOverride);
            if (!arr.isArray() || arr.isEmpty()) break;
            for (JsonNode n : arr) {
                all.add(new PullRequestFile(
                        n.path("filename").asText(""),
                        n.path("status").asText(""),
                        n.path("additions").asInt(0),
                        n.path("deletions").asInt(0),
                        n.path("patch").asText("")
                ));
            }
        }
        return all;
    }

    public String getFileContent(String owner, String repo, String path, String ref) {
        return getFileContent(owner, repo, path, ref, null);
    }

    public String getFileContent(String owner, String repo, String path, String ref, String tokenOverride) {
        String finalOwner = pick(owner, defaultOwner);
        String finalRepo = pick(repo, defaultRepo);
        String finalRef = (ref == null || ref.isBlank()) ? "main" : ref.trim();

        String url = String.format("https://api.github.com/repos/%s/%s/contents/%s?ref=%s",
                finalOwner, finalRepo, path, finalRef);
        JsonNode root = getJson(url, tokenOverride);
        String contentBase64 = root.path("content").asText("");
        if (contentBase64.isBlank()) return "";
        byte[] decoded = Base64.getDecoder().decode(contentBase64.replaceAll("\\s", ""));
        return new String(decoded, StandardCharsets.UTF_8);
    }

    public String getPullRequestDiff(String owner, String repo, int prNumber, String tokenOverride) {
        String finalOwner = pick(owner, defaultOwner);
        String finalRepo = pick(repo, defaultRepo);
        String url = String.format("https://api.github.com/repos/%s/%s/pulls/%d", finalOwner, finalRepo, prNumber);
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3.diff")
                .header("X-GitHub-Api-Version", "2022-11-28");
        String token = (tokenOverride != null && !tokenOverride.isBlank()) ? tokenOverride.trim() :
                (githubToken == null ? "" : githubToken.trim());
        if (!token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        try (Response response = httpClient.newCall(builder.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException("GitHub API failed: " + response.code() + " " + response.message());
            }
            return response.body().string();
        } catch (IOException e) {
            throw new IllegalStateException("GitHub API request failed: " + e.getMessage(), e);
        }
    }

    private JsonNode getJson(String url, String tokenOverride) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28");
        String token = (tokenOverride != null && !tokenOverride.isBlank()) ? tokenOverride.trim() :
                (githubToken == null ? "" : githubToken.trim());
        if (!token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("GitHub API failed: " + response.code() + " " + response.message());
            }
            if (response.body() == null) {
                throw new IllegalStateException("GitHub API failed: empty body");
            }
            String json = response.body().string();
            return objectMapper.readTree(json);
        } catch (IOException e) {
            throw new IllegalStateException("GitHub API request failed: " + e.getMessage(), e);
        }
    }

    private static String pick(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) return preferred.trim();
        return fallback == null ? "" : fallback.trim();
    }

    private static String text(JsonNode root, String field) {
        if (root == null) return "";
        JsonNode n = root.get(field);
        return n == null || n.isNull() ? "" : n.asText("");
    }

    public record PullRequestInfo(String owner, String repo, int prNumber, String title, String body, String headSha) {
    }

    public record PullRequestFile(String filename, String status, int additions, int deletions, String patch) {
    }
}
