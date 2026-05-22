package com.jasmine.studioai.service;

import com.jasmine.studioai.prompt.PromptTemplateService;
import com.jasmine.studioai.retriever.HybridContentRetriever;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiAnswerService {

    private final ChatLanguageModel chatLanguageModel;
    private final HybridContentRetriever retriever;
    private final ConversationOrchestrator conversationOrchestrator;
    private final PromptTemplateService promptTemplateService;

    public String answer(String userId, String sessionId, String question, String mode) {
        String m = normalizeMode(mode);
        String augmented = conversationOrchestrator.augmentQuestion(userId, sessionId, question);

        if ("external".equals(m)) {
            return chatLanguageModel.chat(augmented);
        }

        var probe = retriever.probe(question == null ? "" : question, 5);
        var scored = probe == null ? java.util.List.<HybridContentRetriever.ScoredText>of() : probe.contexts();
        boolean hasAnyText = scored.stream().anyMatch(s -> s != null && s.text() != null && !s.text().isBlank());
        boolean looksRelevant = (probe != null) && (probe.bm25Count() > 0 || probe.maxVectorScore() >= 0.25);
        boolean hasContext = hasAnyText && looksRelevant;

        if (!hasContext) {
            if ("local".equals(m)) {
                return "知识库暂未找到相关内容。我只能基于本地资料回答：建议先上传相关文档，或切换到外部回答模式。";
            }
            // auto: fallback to external model
            return chatLanguageModel.chat(augmented);
        }

        String contexts = scored.stream()
                .filter(s -> s != null && s.text() != null && !s.text().isBlank())
                .map(s -> s.text().trim())
                .limit(5)
                .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = promptTemplateService.render(
                "prompts/kb/local_answer.txt",
                "default",
                PromptTemplateService.vars()
                        .put("contexts", contexts)
                        .put("augmentedQuestion", augmented)
                        .build()
        );

        return chatLanguageModel.chat(prompt);
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) return "auto";
        String m = mode.trim().toLowerCase();
        if ("local".equals(m) || "external".equals(m) || "auto".equals(m)) return m;
        return "auto";
    }
}
