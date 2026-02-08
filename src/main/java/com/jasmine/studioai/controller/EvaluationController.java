package com.jasmine.studioai.controller;

import com.jasmine.studioai.backup.BackupService;
import com.jasmine.studioai.evaluation.RagEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/evaluation")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "评测体系", description = "RAG 评测与 Prompt A/B 测试")
public class EvaluationController {

    private final RagEvaluationService evaluationService;
    private final BackupService backupService;

    @Operation(summary = "评测答案忠实度")
    @PostMapping("/faithfulness")
    public RagEvaluationService.EvalResult evaluateFaithfulness(@RequestBody EvalRequest request) {
        return evaluationService.evaluateFaithfulness(request.getAnswer(), request.getContext());
    }

    @Operation(summary = "评测检索相关性")
    @PostMapping("/relevance")
    public RagEvaluationService.EvalResult evaluateRelevance(@RequestBody EvalRequest request) {
        return evaluationService.evaluateRelevance(request.getQuery(), request.getContext());
    }

    @Operation(summary = "Prompt A/B 测试")
    @PostMapping("/ab-test")
    public RagEvaluationService.AbTestResult abTest(@RequestBody AbTestRequest request) {
        return evaluationService.comparePromptTemplates(
                request.getOutputA(), request.getOutputB(), request.getInput());
    }

    @Operation(summary = "手动备份")
    @PostMapping("/backup")
    public Map<String, String> manualBackup() throws IOException {
        String filename = backupService.manualBackup();
        return Map.of("file", filename, "status", "completed");
    }

    @Data
    public static class EvalRequest {
        private String query;
        private String answer;
        private String context;
    }

    @Data
    public static class AbTestRequest {
        private String input;
        private String outputA;
        private String outputB;
    }
}
