<p align="center">
  <img src="https://img.shields.io/badge/Java-17-orange?logo=openjdk" alt="Java 17">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen?logo=springboot" alt="Spring Boot 3.2.5">
  <img src="https://img.shields.io/badge/LangChain4j-1.0.0--beta3-blue" alt="LangChain4j">
  <img src="https://img.shields.io/badge/Pinecone-VectorDB-purple" alt="Pinecone">
  <img src="https://img.shields.io/badge/Redis-7-red?logo=redis" alt="Redis">
  <img src="https://img.shields.io/badge/PostgreSQL-16-blue?logo=postgresql" alt="PostgreSQL">
  <img src="https://img.shields.io/badge/Docker-deployed-blue?logo=docker" alt="Docker">
</p>

<h1 align="center">TeamFlow AI</h1>
<p align="center"><strong>基于 LangChain4j 的团队级 AI Agent 平台</strong></p>

<p align="center">
  <a href="https://studio-ai-production.up.railway.app/swagger-ui.html">Live Demo (Swagger)</a>
</p>

---

## 项目简介

从零搭建的团队 AI Agent 平台，集成 **Tool Calling**、**MCP 协议**、**混合检索 RAG**、**多模态知识库**与**分层会话记忆**，覆盖代码审查、知识问答、文献分析、实验记录等场景。130+ 源文件独立开发。

## 核心特性

| 模块 | 技术方案 | 亮点 |
|------|----------|------|
| **Agent 工具调用** | LangChain4j @Tool + MCP 协议 | AI 自主调用 GitHub API，89 种操作 |
| **混合检索 RAG** | Pinecone 向量 + Redis BM25 倒排索引 | 召回率 72%→91% |
| **Code Review** | Prompt 模板 + 反馈闭环 | 误报率 40%→15% |
| **多模态接入** | B 站扫码→ASR 转写→向量化管道 | 视频音频可变检索 |
| **流式对话** | SSE Server-Sent Events | 逐 token 实时推送 |
| **记忆系统** | 滑动窗口 + 摘要压缩 + 向量召回 | 短期 + 长期双层架构 |
| **工程化** | Rate Limit / Audit Log / 数据脱敏 / 定时备份 | 生产可用 |
| **部署** | Docker Compose + Railway 公网 | 一键部署 |

## 技术栈

**后端**：Java 17 · Spring Boot 3.2 · LangChain4j 1.0.0-beta3 · Spring Data JPA · Redis · Flyway

**AI & 检索**：DashScope Qwen-Max · text-embedding-v3 · Pinecone · Apache Tika · OKHttp

**前端**：React 18 + TypeScript + Tailwind CSS + Zustand（独立搭建）

**运维**：Docker Compose · Railway · Prometheus + Actuator · GitHub Actions

## 项目结构

```
studio-ai/
├── src/main/java/com/jasmine/studioai/
│   ├── service/          # AI 服务、会话编排、记忆
│   ├── controller/       # REST 控制器（AI/评测/管理）
│   ├── kb/               # 知识库（查/增/删/索引重建）
│   ├── literature/       # 文献工作台
│   ├── codereview/       # 代码审查 + PR 审查
│   ├── auth/             # 认证（Bearer Token）
│   ├── security/jwt/     # JWT 认证（可选）
│   ├── retriever/        # 混合检索器（BM25 + Vector）
│   ├── rag/              # 高级 RAG + 语义缓存
│   ├── streaming/        # SSE 流式推送
│   ├── websocket/        # WebSocket 协作
│   ├── ratelimit/        # 令牌桶限流
│   ├── model/            # JPA 实体（10 张表）
│   └── repository/       # Spring Data JPA
├── src/main/resources/
│   ├── prompts/          # 12 套 Prompt 模板
│   ├── db/migration/     # Flyway 迁移脚本
│   └── i18n/             # 国际化（中/英/日）
└── frontend/             # React + TypeScript SPA
```

## 快速开始

```bash
# 1) 启动（dev 模式，H2 内存数据库，无需任何外部服务）
mvn spring-boot:run

# 2) 访问
# Swagger API 文档：http://localhost:8090/swagger-ui.html
# 主页：http://localhost:8090/
# 默认账号：admin / admin123
```

## Docker 部署

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

## 架构图

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  React SPA   │────▶│ Spring Boot  │────▶│  PostgreSQL  │
│  (Port 3000) │     │  (Port 8090) │     │  H2 (dev)    │
└──────────────┘     └──────┬───────┘     └──────────────┘
                            │
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
        ┌──────────┐ ┌──────────┐ ┌──────────────┐
        │  Redis   │ │ Pinecone │ │ DashScope     │
        │ BM25     │ │ Vector   │ │ Qwen LLM      │
        │ Session  │ │ Store    │ │ Embedding     │
        └──────────┘ └──────────┘ └──────────────┘
```
