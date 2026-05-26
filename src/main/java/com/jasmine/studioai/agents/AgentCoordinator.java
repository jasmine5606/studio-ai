package com.jasmine.studioai.agents;

import com.jasmine.studioai.codereview.AutoPrReviewService;
import com.jasmine.studioai.github.GitHubApiClient;
import com.jasmine.studioai.service.AiAnswerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCoordinator {

    private final CodeReviewerAgent codeReviewerAgent;
    private final ContentSummarizer contentSummarizer;
    private final ReportGenerator reportGenerator;
    private final AiAnswerService aiAnswerService;
    private final GitHubApiClient gitHubApiClient;
    private final AutoPrReviewService autoPrReviewService;

    private static final Pattern REVIEW_PR = Pattern.compile("(?i)(审查|review|check)\\s*(pr|pull.?request)\\s*#?(\\d+)");
    private static final Pattern GENERATE_REPORT = Pattern.compile("(?i)(生成|写|做).*?(周报|报告|report|weekly)");
    private static final Pattern SUMMARIZE = Pattern.compile("(?i)(总结|概括|摘要|summarize)");

    public CoordinatorResult handle(String userId, String input) {
        String intent = classifyIntent(input);
        log.info("Multi-Agent intent: {} for input: {}", intent, input);

        return switch (intent) {
            case "code_review_thread" -> handleReviewThread(userId, input);
            case "generate_report" -> handleReportGeneration(userId, input);
            default -> handleGeneralTask(userId, input);
        };
    }

    private CoordinatorResult handleReviewThread(String userId, String input) {
        List<String> steps = new ArrayList<>();
        String intent = "code_review_thread";
        steps.add("🔍 分析任务：审查 PR");

        String[] ownerRepo = extractOwnerRepo(input);
        String owner = ownerRepo[0] != null ? ownerRepo[0] : "jasmine5606";
        String repo = ownerRepo[1] != null ? ownerRepo[1] : "jasmine";
        String prNumber = extractPrNumber(input);

        steps.add("📥 正在获取 PR #" + prNumber + " 变更列表...");

        try {
            var request = new com.jasmine.studioai.codereview.dto.AutoPrReviewRequest();
            request.setOwner(owner);
            request.setRepo(repo);
            request.setPrNumber(Integer.parseInt(prNumber));
            request.setMaxFiles(5);
            var response = autoPrReviewService.review(request);

            String reviewResult = response.getSummary() != null ? response.getSummary() : "审查完成";
            steps.add("📝 审查完成，正在汇总...");
            String summary = contentSummarizer.summarize(reviewResult);

            return new CoordinatorResult(summary, steps, intent);
        } catch (Exception e) {
            log.error("PR review failed", e);
            steps.add("❌ 审查失败: " + e.getMessage());
            return new CoordinatorResult("审查过程中出现错误: " + e.getMessage(), steps, "error");
        }
    }

    private CoordinatorResult handleReportGeneration(String userId, String input) {
        List<String> steps = new ArrayList<>();
        steps.add("📊 开始生成周报...");
        steps.add("📥 收集本周代码审查数据...");

        String context = aiAnswerService.answer(userId, "report", "生成一份项目周报所需的背景信息", "external");
        steps.add("📋 收集项目背景信息完成");

        steps.add("📝 正在撰写报告...");
        String report = reportGenerator.generate(context);

        steps.add("✅ 报告生成完成");
        return new CoordinatorResult(report, steps, "generate_report");
    }

    private CoordinatorResult handleGeneralTask(String userId, String input) {
        List<String> steps = new ArrayList<>();
        steps.add("🧠 识别为通用任务");

        String hasSummarize = SUMMARIZE.matcher(input).find() ? "summarize" : input;
        if ("summarize".equals(hasSummarize)) {
            String summary = contentSummarizer.summarize(input);
            steps.add("✅ 摘要生成完成");
            return new CoordinatorResult(summary, steps, "summarize");
        }

        String answer = aiAnswerService.answer(userId, "multi", input, "auto");
        steps.add("✅ 单 Agent 处理完成");
        return new CoordinatorResult(answer, steps, "general");
    }

    private String classifyIntent(String input) {
        if (REVIEW_PR.matcher(input).find()) return "code_review_thread";
        if (GENERATE_REPORT.matcher(input).find()) return "generate_report";
        if (SUMMARIZE.matcher(input).find()) return "summarize";
        return "general";
    }

    private String extractPrNumber(String input) {
        Matcher m = REVIEW_PR.matcher(input);
        if (m.find()) return m.group(3);
        Matcher m2 = Pattern.compile("#(\\d+)").matcher(input);
        if (m2.find()) return m2.group(1);
        return "1";
    }

    private String[] extractOwnerRepo(String input) {
        Matcher m = Pattern.compile("(\\w+)/(\\w+)").matcher(input);
        if (m.find()) return new String[]{m.group(1), m.group(2)};
        return new String[]{null, null};
    }

    public record CoordinatorResult(String result, List<String> steps, String intent) {}
}
