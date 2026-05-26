package com.jasmine.studioai.service;

import com.jasmine.studioai.prompt.PromptTemplateService;
import com.jasmine.studioai.retriever.HybridContentRetriever;
import com.jasmine.studioai.streaming.StepEvent;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiAnswerService {

    private final ChatLanguageModel chatLanguageModel;
    private final HybridContentRetriever retriever;
    private final ConversationOrchestrator conversationOrchestrator;
    private final PromptTemplateService promptTemplateService;

    public String answer(String userId, String sessionId, String question, String mode) {
        return answerWithSteps(userId, sessionId, question, mode, null);
    }

    public String answerWithSteps(String userId, String sessionId, String question,
                                   String mode, Consumer<StepEvent> onStep) {
        String m = normalizeMode(mode);

        if (onStep != null) onStep.accept(StepEvent.start("memory", "加载会话记忆..."));
        String augmented = conversationOrchestrator.augmentQuestion(userId, sessionId, question);
        if (onStep != null) onStep.accept(StepEvent.done("memory"));

        if ("external".equals(m)) {
            if (onStep != null) onStep.accept(StepEvent.start("llm", "调用外部模型..."));
            String result = chatLanguageModel.chat(augmented);
            if (onStep != null) onStep.accept(StepEvent.done("llm"));
            return result;
        }

        if (onStep != null) onStep.accept(StepEvent.start("retrieval", "检索知识库..."));
        var probe = retriever.probe(question == null ? "" : question, 5);
        var scored = probe == null ? java.util.List.<HybridContentRetriever.ScoredText>of() : probe.contexts();
        boolean hasAnyText = scored.stream().anyMatch(s -> s != null && s.text() != null && !s.text().isBlank());
        boolean looksRelevant = (probe != null) && (probe.bm25Count() > 0 || probe.maxVectorScore() >= 0.25);
        boolean hasContext = hasAnyText && looksRelevant;

        int bm25Count = probe == null ? 0 : probe.bm25Count();
        double maxVecScore = probe == null ? 0 : probe.maxVectorScore();
        if (onStep != null) onStep.accept(StepEvent.done("retrieval",
                "向量命中 " + String.format("%.1f", maxVecScore * 100) + "% | BM25 命中 " + bm25Count + " 条"));

        if (!hasContext) {
            if ("local".equals(m)) {
                if (onStep != null) onStep.accept(StepEvent.done("llm", "知识库无相关内容"));
                return "知识库暂未找到相关内容。我只能基于本地资料回答：建议先上传相关文档，或切换到外部回答模式。";
            }
            if (onStep != null) onStep.accept(StepEvent.start("llm", "无匹配文档，使用通用模型回答..."));
            String result = chatLanguageModel.chat(augmented);
            if (onStep != null) onStep.accept(StepEvent.done("llm"));
            return result;
        }

        String contexts = scored.stream()
                .filter(s -> s != null && s.text() != null && !s.text().isBlank())
                .map(s -> s.text().trim())
                .limit(5)
                .collect(Collectors.joining("\n\n---\n\n"));

        if (onStep != null) onStep.accept(StepEvent.start("llm", "基于 " + Math.min(5, scored.size()) + " 条文档生成回答..."));

        String prompt = promptTemplateService.render(
                "prompts/kb/local_answer.txt",
                "default",
                PromptTemplateService.vars()
                        .put("contexts", contexts)
                        .put("augmentedQuestion", augmented)
                        .build()
        );

        String result = chatLanguageModel.chat(prompt);
        if (onStep != null) onStep.accept(StepEvent.done("llm"));
        return result;
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) return "auto";
        String m = mode.trim().toLowerCase();
        if ("local".equals(m) || "external".equals(m) || "auto".equals(m)) return m;
        return "auto";
    }
}
