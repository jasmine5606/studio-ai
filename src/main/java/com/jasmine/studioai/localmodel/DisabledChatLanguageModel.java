package com.jasmine.studioai.localmodel;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Fallback chat model that allows the app to start without external AI configuration.
 */
public class DisabledChatLanguageModel implements ChatLanguageModel {

    private final String message;

    public DisabledChatLanguageModel(String message) {
        this.message = message;
    }

    @Override
    public String chat(String message) {
        return this.message;
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(message))
                .build();
    }
}

