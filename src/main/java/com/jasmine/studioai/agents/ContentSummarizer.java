package com.jasmine.studioai.agents;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ContentSummarizer {

    @SystemMessage("""
        你是内容总结专家。将所有输入的信息压缩成简洁的结构化摘要。
        输出格式：
        ## 核心要点
        - 点 1
        - 点 2
        ## 关键数据
        - 数据 1
        ## 待办事项
        - 事项 1
        保持每条摘要不超过 2 行。
        """)
    String summarize(@UserMessage @V("content") String content);
}
