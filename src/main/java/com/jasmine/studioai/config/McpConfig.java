package com.jasmine.studioai.config;

import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.service.tool.ToolProvider;   // 关键：使用正确的 ToolProvider
import dev.langchain4j.service.tool.ToolProviderResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.Map;
import java.util.List;

@Configuration
public class McpConfig {

    @Value("${github.token:}")
    private String githubToken;

    @Bean
    @ConditionalOnProperty(prefix = "mcp", name = "enabled", havingValue = "true")
    public McpClient gitHubMcpClient() {
        McpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of("npx", "-y", "github-repos-manager-mcp"))
                .environment(Map.of("GH_TOKEN", githubToken == null ? "" : githubToken))
                .logEvents(true)
                .build();

        return new dev.langchain4j.mcp.client.DefaultMcpClient.Builder()
                .transport(transport)
                .clientName("studio-ai-github-client")
                .clientVersion("1.0.0")
                .toolExecutionTimeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "mcp", name = "enabled", havingValue = "true")
    public ToolProvider mcpToolProvider(McpClient gitHubMcpClient) {
        return McpToolProvider.builder()
                .mcpClients(List.of(gitHubMcpClient))
                .build();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "mcp", name = "enabled", havingValue = "false", matchIfMissing = true)
    public ToolProvider noopToolProvider() {
        return request -> ToolProviderResult.builder().build();
    }
}
