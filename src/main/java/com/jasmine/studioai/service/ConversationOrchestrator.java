package com.jasmine.studioai.service;

import com.jasmine.studioai.memory.LongTermMemoryService;
import com.jasmine.studioai.prompt.PromptTemplateService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationOrchestrator {

    private final SessionMemoryService sessionMemoryService;
    private final ChatLanguageModel chatLanguageModel;
    private final PromptTemplateService promptTemplateService;
    private final LongTermMemoryService longTermMemoryService;

    public String augmentQuestion(String userId, String sessionId, String question) {
        String summary = sessionMemoryService.getSummary(userId, sessionId);
        var window = sessionMemoryService.getWindowMessages(userId, sessionId);
        var pins = sessionMemoryService.getPinnedMemories(userId, 20);
        var recalls = longTermMemoryService.recall(userId, question, 3);

        String history = window.stream()
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

        String pinText = String.join("\n- ", pins);
        if (!pinText.isBlank()) {
            pinText = "- " + pinText;
        }

        String recallText = recalls.stream()
                .map(h -> "- " + h.text())
                .collect(Collectors.joining("\n"));

        return promptTemplateService.render(
                "prompts/memory/augmented_question.txt",
                "default",
                PromptTemplateService.vars()
                        .put("summary", summary)
                        .put("pins", pinText)
                        .put("recalls", recallText)
                        .put("history", history)
                        .put("question", question == null ? "" : question)
                        .build()
        );
    }

    public void summarizeIfNeeded(String userId, String sessionId) {
        if (!sessionMemoryService.shouldSummarize(userId, sessionId)) return;

        var older = sessionMemoryService.getMessagesBeyondWindow(userId, sessionId);
        if (older.isEmpty()) return;

        String olderText = older.stream()
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

        String prevSummary = sessionMemoryService.getSummary(userId, sessionId);

        String prompt = promptTemplateService.render(
                "prompts/memory/summarize.txt",
                "default",
                PromptTemplateService.vars()
                        .put("previousSummary", prevSummary)
                        .put("olderMessages", olderText)
                        .build()
        );

        try {
            String newSummary = chatLanguageModel.chat(prompt);
            sessionMemoryService.setSummary(userId, sessionId, newSummary);
            sessionMemoryService.trimToWindow(userId, sessionId);
        } catch (Exception e) {
            log.warn("Summarize failed (sessionId={}): {}", sessionId, e.getMessage());
        }
    }
}
