package com.jasmine.studioai.controller;

import com.jasmine.studioai.agents.AgentCoordinator;
import com.jasmine.studioai.audit.AuditService;
import com.jasmine.studioai.github.GitHubApiClient;
import com.jasmine.studioai.service.StudioAIAssistant;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "GitHub Webhook", description = "接收 GitHub 事件自动触发 Agent 操作")
public class GitHubWebhookController {

    private final AgentCoordinator agentCoordinator;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Value("${github.webhook.secret:}")
    private String webhookSecret;

    @Operation(summary = "GitHub Webhook 接收端点")
    @PostMapping("/github")
    public ResponseEntity<Map<String, String>> handleGitHubEvent(
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestBody String payload) {
        log.info("Received GitHub event: {}", eventType);

        if ("ping".equals(eventType)) {
            return ResponseEntity.ok(Map.of("msg", "pong"));
        }

        try {
            JsonNode root = objectMapper.readTree(payload);

            switch (eventType) {
                case "pull_request":
                    handlePullRequestEvent(root);
                    break;
                case "issues":
                    handleIssuesEvent(root);
                    break;
                case "push":
                    handlePushEvent(root);
                    break;
                default:
                    log.info("Unhandled event type: {}", eventType);
            }

            return ResponseEntity.ok(Map.of("status", "received"));
        } catch (Exception e) {
            log.error("Webhook processing error", e);
            return ResponseEntity.ok(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @Async
    protected void handlePullRequestEvent(JsonNode root) {
        String action = root.path("action").asText();
        if (!"opened".equals(action) && !"reopened".equals(action) && !"synchronize".equals(action)) {
            return;
        }

        JsonNode pr = root.path("pull_request");
        String prTitle = pr.path("title").asText();
        int prNumber = pr.path("number").asInt();
        String owner = pr.path("base").path("repo").path("owner").path("login").asText();
        String repo = pr.path("base").path("repo").path("name").asText();
        String prUrl = pr.path("html_url").asText();

        log.info("Auto-review triggered: {}/{} PR #{} - {}", owner, repo, prNumber, prTitle);
        auditService.log("webhook", "PR_AUTO_REVIEW", "PR #" + prNumber, prTitle);

        String task = String.format("审查 PR #%d: %s\n仓库: %s/%s\nURL: %s",
                prNumber, prTitle, owner, repo, prUrl);
        var result = agentCoordinator.handle("webhook", task);

        log.info("Auto-review completed for PR #{}. Steps: {}", prNumber, result.steps().size());
    }

    @Async
    protected void handleIssuesEvent(JsonNode root) {
        String action = root.path("action").asText();
        if (!"opened".equals(action)) return;
        String title = root.path("issue").path("title").asText();
        log.info("New issue: {}", title);
    }

    @Async
    protected void handlePushEvent(JsonNode root) {
        String ref = root.path("ref").asText();
        String pusher = root.path("pusher").path("name").asText();
        log.info("Push by {} to {}", pusher, ref);
    }
}
