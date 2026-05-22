package com.jasmine.studioai.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AICodeReviewService {

    private final ChatLanguageModel chatModel;

    public String reviewCode(String code, String language) {
        String prompt = String.format("""
            你是一位资深%s工程师，请审查以下代码，指出：
            1. 潜在bug
            2. 性能问题
            3. 代码规范问题
            4. 可读性建议

            代码：
            ```%s
            %s
            ```

            请用简洁、专业的中文给出审查意见。
            """, language, language, code);
        return chatModel.chat(prompt);
    }

    public String generateUnitTest(String code, String language) {
        String prompt = String.format("""
            为以下%s代码生成单元测试（使用JUnit/TestNG风格）：

            代码：
            ```%s
            %s
            ```

            要求：覆盖主要分支，包含边界条件测试。
            """, language, language, code);
        return chatModel.chat(prompt);
    }
}
