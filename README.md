# Logistics Knowledge Agent MVP

物流行业知识库 + 业务查询 Agent。

这个系统按“知识库 RAG + 业务工具查询 + 权限脱敏 + 审计追踪”的方式实现。当前版本的业务库默认连接云服务器 MySQL，表结构由 Flyway 管理，知识库支持运营接口，Agent 支持回归评测。单元测试仍使用 H2 内存库隔离执行。

## 运行

首次启用本地真实 embedding 前，先下载 BGE-small-zh 的 ONNX 模型到本机。模型文件会放在 `.local-models/`，该目录已被 Git 忽略：

```bash
scripts/download-bge-small-zh-v1.5.sh
```

```bash
set -a
. /Users/zhangzhuang/Documents/script/ssh/spring-ai-demo-db-dev.env
set +a
mvn -Dmaven.repo.local=/Users/zhangzhuang/Documents/develop/maven_repository spring-boot:run
```

启用真实 DeepSeek ChatClient：

```bash
set -a
. /Users/zhangzhuang/Documents/script/ssh/spring-ai-demo-db-dev.env
set +a
mvn -Dmaven.repo.local=/Users/zhangzhuang/Documents/develop/maven_repository spring-boot:run \
  -Dspring-boot.run.arguments="--agent.deepseek.enabled=true --agent.deepseek.api-key-file=/path/to/deepseek-apiKey.txt"
```

云端 MySQL 说明见 [docs/mysql-cloud.md](docs/mysql-cloud.md)。DeepSeek 配置见 [docs/deepseek-chatclient.md](docs/deepseek-chatclient.md)。本机真实向量库选择 PGVector，启动方式见 [docs/pgvector-local.md](docs/pgvector-local.md)。v0.3 知识库运营、Flyway 和评测见 [docs/v03-ops-flyway-eval.md](docs/v03-ops-flyway-eval.md)。v0.4 本地真实 embedding、混合召回、rerank 和 RAG 评测见 [docs/v04-local-embedding-hybrid-rag-eval.md](docs/v04-local-embedding-hybrid-rag-eval.md)。

## 示例请求

客户异常诊断闭环：

```bash
curl -s http://localhost:8080/api/agent/customer-diagnosis \
  -H 'Content-Type: application/json' \
  -d '{
    "conversationId": "conv-diagnosis-001",
    "userId": "u-cs-001",
    "tenantId": "T001",
    "roles": ["CUSTOMER_SERVICE"],
    "customerId": "C001",
    "days": 30,
    "message": "客户 C001 最近 30 天投诉为什么上升，是否满足赔付条件，下一步怎么处理？",
    "returnCitations": true
  }'
```

通用问答：

```bash
curl -s http://localhost:8080/api/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "conversationId": "conv-demo-001",
    "userId": "u-cs-001",
    "tenantId": "T001",
    "roles": ["CUSTOMER_SERVICE"],
    "message": "客户 C001 最近 30 天为什么投诉量上升？相关处理制度是什么？",
    "returnCitations": true
  }'
```

更多问题见 [docs/demo-questions.md](docs/demo-questions.md)。

## API

- `POST /api/agent/chat`：自然语言问答
- `POST /api/agent/customer-diagnosis`：客户异常诊断闭环，返回结构化指标、归因、SLA/赔付候选、引用和审计 trace
- `POST /api/knowledge/documents`：新增或更新知识文档，并自动切 chunk、同步向量库
- `GET /api/knowledge/documents`：查询知识文档列表
- `POST /api/knowledge/documents/{docId}/disable`：停用知识文档并重建向量索引
- `GET /api/knowledge/search`：知识库搜索预览
- `GET /api/agent/evals/cases`：查看 Agent 评测用例
- `POST /api/agent/evals/run`：运行 Agent 回归评测
- `GET /api/agent/evals/runs/{runId}`：查看一次评测结果
- `GET /api/demo/questions`：演示问题
- `GET /api/demo/vector-store/status`：查看 PGVector 知识库状态
- `GET /api/agent/audit/{traceId}`：查看一次问答审计
- `GET /api/agent/audit?tenantId=T001&customerId=C001&limit=20`：按条件查询审计记录

## 设计说明

当前版本没有让模型直连数据库。所有业务数据都通过工具服务查询，知识库检索按租户和角色过滤，最终回答会返回引用、工具调用摘要和 traceId。

知识检索默认使用本机 `BAAI/bge-small-zh-v1.5` ONNX 模型生成 512 维向量，不调用云端 embedding 服务。集成测试为了速度和稳定性仍使用 hashing provider；真实本地 embedding 可用 `TransformersEmbeddingModelTests` 的本地 BGE 参数单独验证。

`agent.demo.reset-on-start` 默认是 `false`，云端 MySQL 不会在每次启动时被清空。测试环境仍设置为 `true`，用于每次测试前重建稳定的模拟数据。

v0.2 客户异常诊断闭环见 [docs/customer-diagnosis-v02.md](docs/customer-diagnosis-v02.md)。
