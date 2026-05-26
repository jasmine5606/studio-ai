package com.jasmine.studioai.config;

import com.jasmine.studioai.agents.CodeReviewerAgent;
import com.jasmine.studioai.agents.ContentSummarizer;
import com.jasmine.studioai.agents.ReportGenerator;
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
import dev.langchain4j.service.tool.ToolProvider;
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
            GitHubTool gitHubTool) {
        return AiServices.builder(StudioAIAssistant.class)
                .chatLanguageModel(chatLanguageModel)
                .contentRetriever(retriever)
                .toolProvider(mcpToolProvider)
                .tools(gitHubTool)
                .build();
    }

    @Bean
    public CodeReviewerAgent codeReviewerAgent(ChatLanguageModel chatLanguageModel) {
        return AiServices.builder(CodeReviewerAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .build();
    }

    @Bean
    public ContentSummarizer contentSummarizer(ChatLanguageModel chatLanguageModel) {
        return AiServices.builder(ContentSummarizer.class)
                .chatLanguageModel(chatLanguageModel)
                .build();
    }

    @Bean
    public ReportGenerator reportGenerator(ChatLanguageModel chatLanguageModel) {
        return AiServices.builder(ReportGenerator.class)
                .chatLanguageModel(chatLanguageModel)
                .build();
    }
}

