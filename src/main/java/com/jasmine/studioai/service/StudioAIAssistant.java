package com.jasmine.studioai.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface StudioAIAssistant {

    @SystemMessage("""
        你是工作室AI助手，负责回答关于工作室技术栈、项目文档、开发规范的问题。
        请根据提供的知识库内容回答。如果知识库中没有相关信息，请说：
        “根据现有资料，我暂时无法回答这个问题，建议查阅工作室文档或咨询相关同学。”
        回答要简洁、准确，体现技术专业性。
        """)
    String answer(@UserMessage @V("question") String question);
}