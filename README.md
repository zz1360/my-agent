# Logistics Knowledge Agent MVP

物流行业知识库 + 业务查询 Agent。

这个系统按“知识库 RAG + 业务工具查询 + 权限脱敏 + 审计追踪”的方式实现。当前版本的业务库默认连接云服务器 MySQL，表结构由 Flyway 管理，知识库支持运营接口，Agent 支持回归评测。单元测试仍使用 H2 内存库隔离执行。

## 运行

首次启用本地真实 embedding 前，先下载 BGE-small-zh 的 ONNX 模型到本机。模型文件会放在 `.local-models/`，该目录已被 Git 忽略：

```bash
scripts/download-bge-small-zh-v1.5.sh
scripts/download-bge-reranker-base.sh
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

云端 MySQL 说明见 [docs/mysql-cloud.md](docs/mysql-cloud.md)。DeepSeek 配置见 [docs/deepseek-chatclient.md](docs/deepseek-chatclient.md)。本机真实向量库选择 PGVector，启动方式见 [docs/pgvector-local.md](docs/pgvector-local.md)。v0.3 知识库运营、Flyway 和评测见 [docs/v03-ops-flyway-eval.md](docs/v03-ops-flyway-eval.md)。v0.4 本地真实 embedding、混合召回、rerank 和 RAG 评测见 [docs/v04-local-embedding-hybrid-rag-eval.md](docs/v04-local-embedding-hybrid-rag-eval.md)。v0.5 本地 reranker 和 RAG 指标升级见 [docs/v05-local-reranker-rag-metrics.md](docs/v05-local-reranker-rag-metrics.md)。v0.6 知识库运营闭环见 [docs/v06-knowledge-ops-workflow.md](docs/v06-knowledge-ops-workflow.md)。v0.7 RAG 检索质量实验台见 [docs/v07-rag-experiment-lab.md](docs/v07-rag-experiment-lab.md)。v0.8 Agent 动作草稿与人工复核见 [docs/v08-agent-action-workflow.md](docs/v08-agent-action-workflow.md)。v0.9 动作执行适配器与低风险自动化见 [docs/v09-action-execution-automation.md](docs/v09-action-execution-automation.md)。v1.0 业务执行闭环、幂等与重试见 [docs/v10-business-execution-idempotency-retry.md](docs/v10-business-execution-idempotency-retry.md)。

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
- `POST /api/agent/actions/from-diagnosis`：根据客户诊断 trace 生成客户回复、工单备注、赔付复核等动作草稿
- `GET /api/agent/actions`：查询动作草稿，可按客户和状态过滤
- `GET /api/agent/actions/{actionId}`：查看单个动作草稿和证据
- `POST /api/agent/actions/{actionId}/review`：由运营或管理员审批、驳回或标记动作已执行
- `POST /api/agent/actions/{actionId}/execute`：执行单个已审批动作，支持幂等键，高风险动作需显式 `force=true`
- `GET /api/agent/actions/{actionId}/executions`：查看动作执行日志
- `POST /api/agent/actions/executions/{executionId}/retry`：重试失败的动作执行记录
- `POST /api/agent/actions/automation/run`：自动执行客户维度的低风险已审批动作
- `POST /api/knowledge/documents/preview`：预览文档切片，不落库
- `POST /api/knowledge/documents`：新增或更新知识文档，支持版本组、草稿/生效状态和索引任务
- `GET /api/knowledge/documents`：查询知识文档列表，可按状态、业务域、baseDocId 过滤
- `POST /api/knowledge/documents/{docId}/publish`：发布某个文档版本，并让同版本组旧 ACTIVE 版本过期
- `POST /api/knowledge/documents/{docId}/expire`：将知识文档置为过期
- `POST /api/knowledge/documents/{docId}/disable`：停用知识文档并创建索引重建任务
- `GET /api/knowledge/index-jobs`：查询知识索引任务
- `GET /api/knowledge/index-jobs/{jobId}`：查看单个知识索引任务
- `GET /api/knowledge/search`：知识库搜索预览
- `GET /api/rag/experiments`：查看 RAG 检索实验
- `POST /api/rag/experiments`：新增或更新 RAG 检索实验
- `POST /api/rag/experiments/{experimentId}/run`：用不同检索模式运行实验并记录指标
- `GET /api/rag/experiments/{experimentId}/runs`：查看实验运行历史
- `GET /api/agent/evals/cases`：查看 Agent 评测用例
- `POST /api/agent/evals/run`：运行 Agent 回归评测
- `GET /api/agent/evals/runs/{runId}`：查看一次评测结果
- `GET /api/demo/questions`：演示问题
- `GET /api/demo/vector-store/status`：查看 PGVector 知识库状态
- `GET /api/agent/audit/{traceId}`：查看一次问答审计
- `GET /api/agent/audit?tenantId=T001&customerId=C001&limit=20`：按条件查询审计记录

## 设计说明

当前版本没有让模型直连数据库。所有业务数据都通过工具服务查询，知识库检索按租户和角色过滤，最终回答会返回引用、工具调用摘要和 traceId。

知识检索默认使用本机 `BAAI/bge-small-zh-v1.5` ONNX 模型生成 512 维向量，并使用本机 `Xenova/bge-reranker-base` ONNX 模型做候选精排，不调用云端 embedding 或 reranker 服务。集成测试为了速度和稳定性仍使用 hashing embedding 和 lightweight reranker；真实本地模型链路可用专门单测显式验证。

`agent.demo.reset-on-start` 默认是 `false`，云端 MySQL 不会在每次启动时被清空。测试环境仍设置为 `true`，用于每次测试前重建稳定的模拟数据。

v0.2 客户异常诊断闭环见 [docs/customer-diagnosis-v02.md](docs/customer-diagnosis-v02.md)。
