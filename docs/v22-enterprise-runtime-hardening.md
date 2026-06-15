# v2.2 企业运行增强：部署、追踪、错误码与 RAG 审计

v2.2 面向企业化运行继续收口，重点解决四个问题：如何部署、如何排障、错误如何稳定返回、RAG 质量问题如何追溯。

## 1. 容器化部署

新增文件：

- `Dockerfile`：多阶段构建，使用 JDK 21 编译，运行镜像只保留 JRE 和应用 jar。
- `.dockerignore`：排除 Git、IDE、target、本地模型、本地数据和敏感配置。
- `docker-compose.prod.example.yml`：生产部署样例，通过环境变量和 Docker secret 注入配置。
- `scripts/run-local.sh`：本机 local profile 启动脚本。
- `scripts/run-prod.sh`：基于 compose 样例构建并启动生产容器。

本机启动：

```bash
scripts/run-local.sh
```

容器启动前复制 `.env.example` 为 `.env` 并通过安全渠道填充真实值，再执行：

```bash
scripts/run-prod.sh
```

## 2. 统一 trace 日志

新增 `RequestMdcFilter`，每个 HTTP 请求都会写入 MDC：

- `requestId`
- `tenantId`
- `userId`
- `path`

Agent 问答和客户诊断链路会额外写入：

- `traceId`
- `conversationId`

日志格式在 `application.yml` 中统一带上这些字段。排查问题时，可以直接用 `traceId` 串起请求、RAG 检索、工具调用、模型调用和最终审计。

## 3. 全局错误码

新增统一错误响应：

```json
{
  "code": "VALIDATION_ERROR",
  "message": "message 不能为空",
  "status": 400,
  "path": "/api/agent/chat",
  "traceId": "req-xxxx",
  "timestamp": "2026-06-15T00:00:00Z"
}
```

当前错误码：

- `VALIDATION_ERROR`：参数校验失败。
- `ACCESS_DENIED`：权限不足。
- `BAD_REQUEST`：业务参数不合法。
- `AUTH_API_KEY_INVALID`：企业 API Key 缺失或无效。
- `INTERNAL_ERROR`：未预期的系统异常。

## 4. RAG 检索审计

新增表 `ai_agent_rag_audit`，每次 Agent 问答和客户诊断都会记录：

- `trace_id`
- `retrieval_mode`
- `knowledge_version`
- `top_k`
- `vector_ready`
- `vector_used`
- `keyword_used`
- `reranker_used`
- `active_chunk_count`
- `candidate_count`
- `rerank_candidate_count`
- `returned_count`
- `hits_json`

`GET /api/agent/audit/{traceId}` 会返回 `ragAudits`，用于回答质量排查。

当用户问“为什么这次回答引用不对”时，可以根据 trace 看到：

- 当时用的是哪个检索模式。
- PGVector 是否可用。
- 命中了哪些知识版本。
- 候选有多少，最终返回多少。
- top hit 的 docId、chunkId、score、vectorScore、keywordScore、rerankerScore。

## 5. 这一版的价值

v2.2 让项目更接近企业运行状态：可以被容器化部署，可以用 traceId 排障，可以稳定返回错误码，也可以把 RAG 回答质量问题落到可查询的审计证据上。
