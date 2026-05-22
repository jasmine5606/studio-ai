# 未来码实验室 · AI 中心（studio-ai）

Spring Boot + LangChain4j 的团队 AI 中心示例工程：

- 对话助手：RAG（混合检索）+ Tool Calling
- Code Review：代码审查 / 单元测试生成 / GitHub PR 审查
- 知识库：文件上传/更新/删除，Tika 解析（txt/md/pdf/docx/pptx…）+ BM25(Redis) + 向量检索（Pinecone 可选）
- B 站收藏夹同步：公开/登录态两种（私密收藏夹需扫码登录）
- 记忆：短期记忆（滑动窗口 + 摘要 + Redis）+ 长期记忆（主动录入 + 向量化召回）
- 多人使用：登录后会话/记忆按用户隔离；知识库为全员共享

默认端口：`8090`（可用 `SERVER_PORT` 覆盖）

## 本地启动

### 1) 准备 Redis

```bash
docker compose up -d
```

默认连接 `localhost:6379`。

### 2) 配置密钥与账号（推荐使用外部文件）

项目会自动加载根目录的 `application-secrets.yml`（已在 `.gitignore` 中，避免提交到仓库）。

示例（只展示字段结构，不要把密钥提交到仓库）：

```yml
langchain4j:
  community:
    dashscope:
      embedding-model:
        api-key: your_key

dashscope:
  asr:
    api-key: your_key

pinecone:
  api-key: your_key

auth:
  users:
    - username: admin
      password: change_me
      role: ADMIN
    - username: alice
      password: change_me
      role: USER
```

### 3) 运行

```bash
mvn -DskipTests spring-boot:run
```

访问：`http://localhost:8090/`

## 给实验室成员访问（部署）

不要每次都在个人电脑跑后端。推荐部署到一台“团队可访问”的服务器（内网或云主机），开放 `8090` 端口。

### 方案 A：直接跑 Jar

```bash
mvn -DskipTests package
java -jar target/*.jar
```

常用环境变量：

- `SERVER_PORT`：端口（默认 8090）
- `SERVER_ADDRESS`：监听地址（默认 `0.0.0.0`）
- `REDIS_HOST/REDIS_PORT/REDIS_PASSWORD`：Redis 连接

### 方案 B：Docker Compose 一键部署（推荐）

```bash
mvn -DskipTests package
docker compose -f docker-compose.prod.yml up -d --build
```

`docker-compose.prod.yml` 会启动 `redis` 和 `studio-ai`，并把 `./application-secrets.yml` 只读挂载到容器内。

## 使用建议（多人 + B站）

- 登录后：每个人只看到自己的会话/记忆；知识库为公共。
- B 站收藏夹同步：建议由管理员扫码绑定一次（共享账号），用于把收藏夹内容同步进公共知识库（团队可检索）。

