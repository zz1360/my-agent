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

启动后可访问 `http://localhost:8080/chat.html` 打开轻量聊天页，也可以访问 `http://localhost:8080/admin/actions.html` 打开轻量管理台。

云端 MySQL 说明见 [docs/mysql-cloud.md](docs/mysql-cloud.md)。DeepSeek 配置见 [docs/deepseek-chatclient.md](docs/deepseek-chatclient.md)。本机真实向量库选择 PGVector，启动方式见 [docs/pgvector-local.md](docs/pgvector-local.md)。v0.3 知识库运营、Flyway 和评测见 [docs/v03-ops-flyway-eval.md](docs/v03-ops-flyway-eval.md)。v0.4 本地真实 embedding、混合召回、rerank 和 RAG 评测见 [docs/v04-local-embedding-hybrid-rag-eval.md](docs/v04-local-embedding-hybrid-rag-eval.md)。v0.5 本地 reranker 和 RAG 指标升级见 [docs/v05-local-reranker-rag-metrics.md](docs/v05-local-reranker-rag-metrics.md)。v0.6 知识库运营闭环见 [docs/v06-knowledge-ops-workflow.md](docs/v06-knowledge-ops-workflow.md)。v0.7 RAG 检索质量实验台见 [docs/v07-rag-experiment-lab.md](docs/v07-rag-experiment-lab.md)。v0.8 Agent 动作草稿与人工复核见 [docs/v08-agent-action-workflow.md](docs/v08-agent-action-workflow.md)。v0.9 动作执行适配器与低风险自动化见 [docs/v09-action-execution-automation.md](docs/v09-action-execution-automation.md)。v1.0 业务执行闭环、幂等与重试见 [docs/v10-business-execution-idempotency-retry.md](docs/v10-business-execution-idempotency-retry.md)。v1.1 执行可观测性与管理后台接口见 [docs/v11-execution-observability-admin.md](docs/v11-execution-observability-admin.md)。v1.2 轻量管理页面见 [docs/v12-action-admin-console.md](docs/v12-action-admin-console.md)。v1.3 对话历史、流式输出与反馈闭环见 [docs/v13-conversation-stream-feedback.md](docs/v13-conversation-stream-feedback.md)。v1.4 反馈样本池、评测候选与 RAG 实验闭环见 [docs/v14-feedback-eval-rag-loop.md](docs/v14-feedback-eval-rag-loop.md)。v1.5 评测候选标注、审批与质量看板见 [docs/v15-candidate-annotation-quality-dashboard.md](docs/v15-candidate-annotation-quality-dashboard.md)。v1.6 质量趋势与候选审计见 [docs/v16-quality-trend-audit.md](docs/v16-quality-trend-audit.md)。v1.7 质量告警、标签治理和评测集版本化见 [docs/v17-quality-alert-tag-governance-eval-suite.md](docs/v17-quality-alert-tag-governance-eval-suite.md)。v1.8 可编辑治理后台、告警任务化与评测版本追踪见 [docs/v18-editable-governance-alert-task-versioned-eval.md](docs/v18-editable-governance-alert-task-versioned-eval.md)。v1.9 质量运营闭环增强、评测对比与检索灰度见 [docs/v19-quality-ops-eval-compare-retrieval-gray.md](docs/v19-quality-ops-eval-compare-retrieval-gray.md)。v2.1 企业 Profile、可观测性与 CI 发布门禁见 [docs/v21-enterprise-ops-profile-ci.md](docs/v21-enterprise-ops-profile-ci.md)。

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

- `GET /chat.html`：轻量聊天页，支持流式回答、历史会话、反馈、引用、工具调用、动作草稿生成和审计 trace
- `GET /admin/actions.html`：轻量动作管理台页面，聚合动作列表、执行日志、重试队列、指标、业务回链、反馈样本池、评测候选池、反馈质量看板、质量趋势、标签字典维护、质量告警、告警规则维护、告警任务流转、治理趋势图、候选操作审计、评测集版本、评测结果对比和检索模式灰度
- `POST /api/agent/chat`：自然语言问答
- `POST /api/agent/chat/stream`：SSE 流式问答，输出状态、回答增量和最终完整响应
- `GET /api/agent/conversations`：查询当前用户的历史会话列表
- `GET /api/agent/conversations/{conversationId}`：查看单个会话及消息历史
- `POST /api/agent/messages/{messageId}/feedback`：记录某条 Agent 回答的有用性反馈
- `GET /api/agent/feedback`：查询回答反馈样本池，默认返回 `NOT_HELPFUL` 样本
- `GET /api/agent/eval-candidates`：查询由反馈生成的评测候选
- `POST /api/agent/feedback/{feedbackId}/eval-candidate`：把负反馈转成评测候选
- `POST /api/agent/eval-candidates/{candidateId}/annotate`：保存评测候选的人工标注、期望引用和反馈标签
- `POST /api/agent/eval-candidates/{candidateId}/review`：审批评测候选，支持通过或驳回
- `POST /api/agent/eval-candidates/{candidateId}/rag-experiment`：由评测候选创建并可立即运行 RAG 实验
- `POST /api/agent/eval-candidates/{candidateId}/promote`：把评测候选沉淀为正式评测用例
- `GET /api/agent/feedback/quality-metrics`：查看负反馈率、候选转化率、审批通过率和实验通过率，支持 `from/to` 日期窗口并返回每日趋势
- `GET /api/agent/eval-candidate-audits`：查询评测候选创建、标注、审批、RAG 实验和转评测的操作审计
- `GET /api/agent/quality/feedback-tags`：查询反馈标签字典
- `POST /api/agent/quality/feedback-tags/{tagCode}`：新增或更新反馈标签字典项，支持名称、分类、排序、启停和说明
- `GET /api/agent/quality/alert-rules`：查询质量告警规则
- `POST /api/agent/quality/alert-rules/{ruleId}`：新增或更新质量告警规则，支持指标、阈值、窗口、级别和启停
- `GET /api/agent/quality/alerts`：查询质量告警记录
- `POST /api/agent/quality/alerts/evaluate`：评估质量告警规则并生成或恢复告警
- `POST /api/agent/quality/alerts/{alertId}/task`：将质量告警转成运营复核任务，并回写任务 ID
- `GET /api/agent/quality/alert-tasks`：查询由质量告警生成的运营复核任务
- `POST /api/agent/quality/alert-tasks/{taskId}/transition`：流转告警任务状态，支持负责人和处理备注
- `GET /api/agent/quality/trends`：查看质量告警打开、恢复、任务创建和任务完成趋势
- `POST /api/agent/customer-diagnosis`：客户异常诊断闭环，返回结构化指标、归因、SLA/赔付候选、引用和审计 trace
- `POST /api/agent/actions/from-diagnosis`：根据客户诊断 trace 生成客户回复、工单备注、赔付复核等动作草稿
- `GET /api/agent/actions`：查询动作草稿，可按客户和状态过滤
- `GET /api/agent/actions/{actionId}`：查看单个动作草稿和证据
- `POST /api/agent/actions/{actionId}/review`：由运营或管理员审批、驳回或标记动作已执行
- `POST /api/agent/actions/{actionId}/execute`：执行单个已审批动作，支持幂等键，高风险动作需显式 `force=true`
- `GET /api/agent/actions/executions`：搜索动作执行日志
- `GET /api/agent/actions/executions/retry-queue`：查看失败重试队列
- `GET /api/agent/actions/executions/metrics`：查看执行成功率、失败率和分组指标
- `GET /api/agent/actions/{actionId}/executions`：查看动作执行日志
- `GET /api/agent/actions/{actionId}/business-link`：查看动作写入的业务表记录
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
- `GET /api/knowledge/search`：知识库搜索预览，支持 `mode=keyword/vector/hybrid/hybrid_reranker`
- `GET /api/knowledge/search/preview`：带分数明细的知识库检索预览，用于检索模式灰度对比
- `GET /api/rag/experiments`：查看 RAG 检索实验
- `POST /api/rag/experiments`：新增或更新 RAG 检索实验
- `POST /api/rag/experiments/{experimentId}/run`：用不同检索模式运行实验并记录指标
- `GET /api/rag/experiments/{experimentId}/runs`：查看实验运行历史
- `GET /api/agent/evals/cases`：查看 Agent 评测用例
- `GET /api/agent/evals/suites`：查看评测集版本和用例数量
- `GET /api/agent/evals/runs`：查看最近评测运行列表
- `GET /api/agent/evals/runs/compare`：对比两次评测运行，输出退化、提升、新增和移除用例
- `POST /api/agent/evals/run`：运行 Agent 回归评测，可传 `modelVersion/knowledgeVersion/promptVersion`
- `POST /api/agent/evals/suites/{suiteId}/run`：按指定评测集版本运行回归评测，可传 `modelVersion/knowledgeVersion/promptVersion`
- `GET /api/agent/evals/runs/{runId}`：查看一次评测结果
- `GET /api/demo/questions`：演示问题
- `GET /api/demo/vector-store/status`：查看 PGVector 知识库状态
- `GET /api/agent/audit/{traceId}`：查看一次问答审计
- `GET /api/agent/audit?tenantId=T001&customerId=C001&limit=20`：按条件查询审计记录
- `GET /api/ops/readiness`：查看业务库、Flyway、PGVector、DeepSeek 和默认检索策略是否就绪
- `GET /api/ops/metrics/summary`：查看问答量、平均延迟、RAG recall、工具成功率和发布门禁统计
- `GET /actuator/health`：Spring Boot Actuator 健康检查，包含 DeepSeek、PGVector、检索策略和 Flyway 版本
- `GET /actuator/metrics`：Spring Boot Actuator 指标入口，包含 `logistics.agent.*` 自定义指标

## 设计说明

当前版本没有让模型直连数据库。所有业务数据都通过工具服务查询，知识库检索按租户和角色过滤，最终回答会返回引用、工具调用摘要和 traceId。

知识检索默认使用本机 `BAAI/bge-small-zh-v1.5` ONNX 模型生成 512 维向量，并使用本机 `Xenova/bge-reranker-base` ONNX 模型做候选精排，不调用云端 embedding 或 reranker 服务。集成测试为了速度和稳定性仍使用 hashing embedding 和 lightweight reranker；真实本地模型链路可用专门单测显式验证。

`agent.demo.reset-on-start` 默认是 `false`，云端 MySQL 不会在每次启动时被清空。测试环境仍设置为 `true`，用于每次测试前重建稳定的模拟数据。

v1.6 开始，反馈质量看板支持日期窗口和每日趋势。候选创建、人工标注、审批、RAG 实验创建和转正式评测都会写入 `ai_eval_case_candidate_audit`，用于追踪反馈样本是如何变成可回归的评测资产。

v1.7 开始，反馈标签沉淀为字典，质量指标可以按规则生成告警，候选审计记录字段级 diff，评测用例也可以按评测集版本运行。

v1.8 开始，标签字典和告警规则可在管理台维护，告警可以转成运营复核任务，评测运行会记录模型版本、知识库版本和提示词版本。

v1.9 开始，告警任务支持处理流转和负责人备注，质量治理趋势升级为图形化展示，评测运行可以按版本做 case 级对比，知识库检索也可以显式选择 `keyword/vector/hybrid/hybrid_reranker` 模式，方便 PGVector 灰度切换。

v2.1 开始，项目补齐 `local/dev/prod` Profile、启动配置校验、Actuator 健康检查、自定义指标摘要、GitHub Actions CI 门禁和 `.env.example` 配置样例，方便按企业应用方式启动、观测和发布。

v0.2 客户异常诊断闭环见 [docs/customer-diagnosis-v02.md](docs/customer-diagnosis-v02.md)。
