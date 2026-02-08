package com.jasmine.studioai;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
public class PineconeUploadTest {

    @Autowired
    private EmbeddingStore embeddingStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Test
    public void uploadDocuments() throws IOException {
        // 1. 获取 knowledge-base 目录下的所有文件，过滤出 .md 和 .txt
        Path kbDir = Paths.get("./knowledge-base");
        if (!Files.exists(kbDir)) {
            System.out.println("⚠️ 目录不存在: " + kbDir.toAbsolutePath());
            return;
        }

        List<Path> filePaths = Files.list(kbDir)
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.toString().toLowerCase();
                    return name.endsWith(".md") || name.endsWith(".txt");
                })
                .collect(Collectors.toList());

        if (filePaths.isEmpty()) {
            System.out.println("⚠️ 未找到 .md 或 .txt 文档，请检查 knowledge-base 目录");
            return;
        }

        // 2. 加载每个文件为 Document
        List<Document> documents = filePaths.stream()
                .map(FileSystemDocumentLoader::loadDocument)
                .collect(Collectors.toList());

        // 3. 创建 Ingestor 并执行上传
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .build();

        ingestor.ingest(documents);

        System.out.println("✅ 成功上传 " + documents.size() + " 个文档到 Pinecone 知识库！");
    }
}