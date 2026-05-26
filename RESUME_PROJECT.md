简历项目描述（可直接复制）

TeamFlow AI    后端负责人  |  Java 17 · Spring Boot 3.2 · LangChain4j · Pinecone · Redis
项目链接：https://github.com/jasmine5606/studio-ai  |  线上演示：https://studio-ai.up.railway.app/swagger-ui.html

项目背景：作为实验室负责人从零搭建团队 AI Agent 平台，集成 Tool Calling、混合检索 RAG、
多模态知识库与会话记忆，覆盖代码审查、知识问答、文献分析等场景，已部署上线。

工作内容
1. 基于 LangChain4j @Tool 实现 Agent 工具调用，使模型自主决策调用 GitHub API；引入 MCP
   协议通过 stdio 动态扩展 89 种 GitHub 操作能力。

2. 构建 Pinecone 向量 + Redis BM25 倒排索引的混合检索架构（0.6:0.4 加权融合），引入
   HyDE 假设文档嵌入增强检索，召回率从 72% 提升至 91%；自研中文分词器实现增量索引。

3. 定制 Code Review 多场景 Prompt 模板，迭代约束规则 + Few-shot 将误报率从 40% 降至
   15%；实现 GitHub PR 自动审查流水线（拉取→分组→审查→LLM 汇总）。

4. 打通 B 站扫码登录→ASR 转写→向量化入库的多模态管道；构建滑动窗口+摘要压缩短期记忆
   与向量召回长期记忆的分层系统。

5. 实现 SSE 流式响应、Bucket4j 限流、会话认证（Bearer Token + ThreadLocal）、Prometheus
   监控等工程化能力，Docker Compose 一键部署，已上线 Railway。
