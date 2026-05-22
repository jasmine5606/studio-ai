package com.jasmine.studioai.config;

import com.jasmine.studioai.retriever.HybridContentRetriever;
import com.jasmine.studioai.service.StudioAIAssistant;
import com.jasmine.studioai.tool.GitHubTool;
import com.jasmine.studioai.localmodel.DisabledChatLanguageModel;
import com.jasmine.studioai.localmodel.HashEmbeddingModel;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;   // 关键：使用正确的 ToolProvider
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AIConfig {

    @Value("${langchain4j.community.dashscope.embedding-model.api-key}")
    private String dashScopeApiKey;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        if (dashScopeApiKey == null || dashScopeApiKey.isBlank()) {
            return new DisabledChatLanguageModel("AI 未配置：请设置环境变量 DASHSCOPE_API_KEY 后重启服务。");
        }
        return OpenAiChatModel.builder()
                .apiKey(dashScopeApiKey)
                .modelName("qwen-max")
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        if (dashScopeApiKey == null || dashScopeApiKey.isBlank()) {
            return new HashEmbeddingModel(1024);
        }
        return QwenEmbeddingModel.builder()
                .apiKey(dashScopeApiKey)
                .modelName("text-embedding-v3")
                .build();
    }

    @Bean
    public StudioAIAssistant studioAIAssistant(
            ChatLanguageModel chatLanguageModel,
            HybridContentRetriever retriever,
            ToolProvider mcpToolProvider,
            GitHubTool gitHubTool) {   // 注入 MCP 工具提供者
        return AiServices.builder(StudioAIAssistant.class)
                .chatLanguageModel(chatLanguageModel)
                .contentRetriever(retriever)
                .toolProvider(mcpToolProvider)
                .tools(gitHubTool)
                .build();
    }
}
