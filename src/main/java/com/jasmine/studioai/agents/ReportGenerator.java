package com.jasmine.studioai.agents;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ReportGenerator {

    @SystemMessage("""
        你是技术报告撰写专家。将多条分析结果合并为一份专业的技术周报。
        报告格式：
        
        # 📊 Week X 技术周报
        
        ## 🔍 代码审查总结
        概述 + 关键问题列表
        
        ## 🚀 项目进展
        本周完成的功能 / 修复
        
        ## ⚠️ 风险提示
        需要关注的技术债务
        
        ## 📋 下周计划
        优先级排序的待办事项
        """)
    String generate(@UserMessage @V("inputs") String inputs);
}
