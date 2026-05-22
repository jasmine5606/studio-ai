package com.jasmine.studioai.config;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.pinecone.PineconeEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Configuration
public class RagConfig {

    @Value("${pinecone.api-key}")
    private String pineconeApiKey;

    @Value("${pinecone.index-name}")
    private String indexName;

    @Value("${pinecone.serverless:true}")   // 默认 true
    private boolean serverless;

    /**
     * 配置 Pinecone 向量存储（适配 Serverless，1.0.0-alpha1）
     * 注意：新版本 builder 中没有 dimension() 方法，维度已在创建索引时固定
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        if (pineconeApiKey == null || pineconeApiKey.isBlank()) {
            return new InMemoryEmbeddingStore<>();
        }
        try {
            return PineconeEmbeddingStore.builder()
                    .apiKey(pineconeApiKey)
                    .index(indexName)
                    .build();
        } catch (Exception e) {
            // Fail-open: allow local run even if Pinecone is misconfigured/unavailable.
            return new InMemoryEmbeddingStore<>();
        }
    }

    /**
     * 初始化知识库：加载本地文档并向量化存储
     * 使用 Java NIO 手动遍历目录并过滤 .md / .txt 文件
     */
    @Bean
    @ConditionalOnProperty(prefix = "kb", name = "init-on-startup", havingValue = "true")
    public Boolean initKnowledgeBase(EmbeddingStore<TextSegment> embeddingStore,
                                     EmbeddingModel embeddingModel) {
        try {
            // 1. 获取 knowledge-base 目录下的所有文件，过滤出 .md 和 .txt
            Path kbDir = Paths.get("./knowledge-base");
            if (!Files.exists(kbDir)) {
                    log.warn("KB init skipped: directory not found {}", kbDir.toAbsolutePath());
                    return false;
                }

                List<Path> filePaths = Files.list(kbDir)
                        .filter(Files::isRegularFile)
                        .filter(path -> {
                            String name = path.toString().toLowerCase();
                            return name.endsWith(".md") || name.endsWith(".txt");
                        })
                        .collect(Collectors.toList());

                if (filePaths.isEmpty()) {
                    log.warn("KB init skipped: no .md/.txt files in {}", kbDir.toAbsolutePath());
                    return false;
                }

                // 2. 加载每个文件为 Document
                List<Document> documents = filePaths.stream()
                        .map(FileSystemDocumentLoader::loadDocument)
                        .collect(Collectors.toList());

                // 3. 创建 Ingestor 并执行向量化存储
                EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel)
                        .build();

                ingestor.ingest(documents);

                log.info("KB init complete: {} documents processed", documents.size());
                return true;

            } catch (IOException e) {
                log.error("KB init failed: {}", e.getMessage());
                return false;
        }
    }
}
