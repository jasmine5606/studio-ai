package com.jasmine.studioai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Base64;

@Component
public class GitHubTool {

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${github.token:}")
    private String githubToken;

    @Value("${github.default-owner:}")
    private String defaultOwner;

    @Value("${github.default-repo:}")
    private String defaultRepo;

    @Tool("读取 GitHub 仓库中指定文件的内容，支持指定所有者、仓库、文件路径和分支")
    public String readGitHubFile(
            @P(value = "仓库所有者（如 'jasmine5606'），可选", required = false) String owner,
            @P(value = "仓库名称（如 'jasmine'），可选", required = false) String repo,
            @P(value = "文件路径（如 'readme.txt'）", required = true) String path,
            @P(value = "分支名称（如 'main' 或 'master'），可选", required = false) String branch
    ) {
        String finalOwner = (owner != null && !owner.isEmpty()) ? owner : defaultOwner;
        String finalRepo = (repo != null && !repo.isEmpty()) ? repo : defaultRepo;
        String finalBranch = (branch != null && !branch.isEmpty()) ? branch : "main";

        if (finalOwner == null || finalOwner.isEmpty()) {
            return "错误：未指定仓库所有者。";
        }
        if (finalRepo == null || finalRepo.isEmpty()) {
            return "错误：未指定仓库名称。";
        }

        try {
            String url = String.format("https://api.github.com/repos/%s/%s/contents/%s?ref=%s",
                    finalOwner, finalRepo, path, finalBranch);
            Request.Builder builder = new Request.Builder().url(url)
                    .header("Accept", "application/vnd.github.v3+json");
            if (githubToken != null && !githubToken.isEmpty()) {
                builder.header("Authorization", "token " + githubToken);
            }
            try (Response response = client.newCall(builder.build()).execute()) {
                if (!response.isSuccessful()) {
                    return "GitHub API 调用失败：" + response.code() + " - " + response.message();
                }
                String json = response.body().string();
                JsonNode root = objectMapper.readTree(json);
                JsonNode contentNode = root.get("content");
                if (contentNode == null) {
                    return "无法获取文件内容（content 字段不存在）。";
                }
                String contentBase64 = contentNode.asText();
                // Base64 解码，去除所有空白字符（包括换行符）
                byte[] decoded = Base64.getDecoder().decode(contentBase64.replaceAll("\\s", ""));
                return new String(decoded);
            }
        } catch (IOException e) {
            return "请求异常：" + e.getMessage();
        }
    }
}