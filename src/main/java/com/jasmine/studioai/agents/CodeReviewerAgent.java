package com.jasmine.studioai.agents;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface CodeReviewerAgent {

    @SystemMessage("""
        你是资深代码审查专家，负责审查代码质量和安全性。
        审查要点：
        1. 逻辑错误和潜在 Bug
        2. 安全漏洞（SQL 注入、XSS、权限问题）
        3. 性能问题（N+1 查询、内存泄漏）
        4. 异常处理缺失
        请勿报告代码风格、命名规范等非功能性建议。
        输出格式：先列出问题数量和严重程度，再逐条说明。
        """)
    String review(@UserMessage @V("code") String code);

    @SystemMessage("""
        你是单元测试专家。根据提供的代码生成完整的 JUnit 5 测试用例。
        要求：
        - 覆盖正常路径和边界条件
        - 包含异常场景测试
        - 使用 Mockito 模拟外部依赖
        - 每个测试方法添加 @DisplayName 中文描述
        """)
    String generateTests(@UserMessage @V("code") String code);
}
