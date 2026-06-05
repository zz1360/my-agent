# v0.3 知识库运营、Flyway 和 Agent 评测

这一版的目标是把物流知识库 Agent 从“能演示”推进到“能维护、能迁移、能回归验证”。

## 这一版做了什么

- 使用 Flyway 管理业务库表结构，不再依赖 Java 启动时创建业务表。
- 增加知识库运营接口，可以新增、更新、停用、查询和重建知识索引。
- 增加 Agent 评测接口，用固定用例验证回答内容、引用、工具调用和风险等级。
- 保留 H2 作为测试库，真实业务库仍使用云服务器 MySQL。
- PGVector 作为本机真实向量库，知识文档变更后会触发同步。

## Flyway 怎么工作

表结构放在：

```text
src/main/resources/db/migration/V1__create_logistics_agent_schema.sql
```

应用启动时，Flyway 会先执行迁移脚本，再运行演示数据初始化逻辑。

关键配置：

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 0
```

`baseline-on-migrate=true` 是为了兼容已经存在旧表的 MySQL 库。旧库没有 `flyway_schema_history` 时，Flyway 会先建立基线，再执行 `V1` 迁移。

以后如果要改表，不建议直接改 `V1`，而是新增：

```text
src/main/resources/db/migration/V2__add_xxx_column.sql
src/main/resources/db/migration/V3__create_xxx_table.sql
```

## 知识库运营接口

新增或更新知识文档：

```bash
curl -s http://localhost:8080/api/knowledge/documents \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "T001",
    "userId": "u-ops-001",
    "roles": ["OPS_MANAGER"],
    "docId": "sop-last-mile-delay",
    "title": "末端派送延误处理 SOP",
    "docType": "sop",
    "bizDomain": "last_mile",
    "version": "v1.0",
    "aclRoles": ["CUSTOMER_SERVICE", "OPERATIONS", "OPS_MANAGER"],
    "content": "末端派送延误超过 4 小时，需要创建异常工单，并同步客户服务人员。若客户等级为 KA，应优先升级到运营经理复核。"
  }'
```

查询知识文档：

```bash
curl -s 'http://localhost:8080/api/knowledge/documents?tenantId=T001&roles=OPS_MANAGER&status=ACTIVE'
```

搜索知识库：

```bash
curl -s 'http://localhost:8080/api/knowledge/search?tenantId=T001&roles=CUSTOMER_SERVICE&query=末端派送延误怎么处理&topK=5'
```

停用知识文档：

```bash
curl -s -X POST 'http://localhost:8080/api/knowledge/documents/sop-last-mile-delay/disable?tenantId=T001&userId=u-ops-001&roles=OPS_MANAGER'
```

重建知识索引：

```bash
curl -s -X POST 'http://localhost:8080/api/knowledge/reindex?tenantId=T001&userId=u-ops-001&roles=OPS_MANAGER'
```

当前实现会把知识文档切成 chunk，写入 `ai_knowledge_chunk`，然后同步到 PGVector。搜索时只返回 `ACTIVE` 文档，并按用户角色过滤。

## Agent 评测接口

查看默认评测用例：

```bash
curl -s 'http://localhost:8080/api/agent/evals/cases?tenantId=T001'
```

运行评测：

```bash
curl -s -X POST 'http://localhost:8080/api/agent/evals/run?tenantId=T001'
```

查看一次评测结果：

```bash
curl -s 'http://localhost:8080/api/agent/evals/runs/{runId}'
```

默认评测覆盖三类场景：

- 运单延误赔付问答：验证回答关键文本、知识引用、工具调用数量和风险等级。
- 客户异常诊断：验证 SLA/赔付候选、人工复核提示、知识引用和业务工具调用数量。
- 提示词注入：验证系统能识别高风险输入。

评测结果会写入：

- `ai_eval_case`
- `ai_eval_run`
- `ai_eval_case_result`

## 本地测试

单元测试使用 H2 MySQL mode，不依赖云服务器：

```bash
mvn -Dmaven.repo.local=/Users/zhangzhuang/Documents/develop/maven_repository test
```

测试覆盖：

- Flyway 是否创建表并写入迁移历史。
- 默认评测用例是否初始化。
- 知识文档能否新增、搜索、停用。
- Agent 默认评测能否通过。
- 原有通用问答、客户诊断和审计能力是否正常。

## 运行真实 MySQL

加载云服务器 MySQL 环境变量：

```bash
set -a
. /Users/zhangzhuang/Documents/script/ssh/spring-ai-demo-db-dev.env
set +a
```

启动应用：

```bash
mvn -Dmaven.repo.local=/Users/zhangzhuang/Documents/develop/maven_repository spring-boot:run
```

如果只想验证 MySQL 和 Flyway，不调用 DeepSeek：

```bash
mvn -Dmaven.repo.local=/Users/zhangzhuang/Documents/develop/maven_repository spring-boot:run \
  -Dspring-boot.run.arguments="--agent.deepseek.enabled=false"
```

默认 `agent.demo.reset-on-start=false`，不会清空云端已有业务数据。如果需要重建演示数据，显式传入：

```bash
mvn -Dmaven.repo.local=/Users/zhangzhuang/Documents/develop/maven_repository spring-boot:run \
  -Dspring-boot.run.arguments="--agent.demo.reset-on-start=true"
```

## 后续可以怎么升级

- 把当前关键词召回升级为 PGVector similarity search。
- 接入真实 embedding 模型，支持混合召回和 rerank。
- 给 `ai_eval_case` 增加运营接口，让评测用例也能通过页面或 API 维护。
- 给知识文档增加审批状态，例如 `DRAFT`、`ACTIVE`、`DISABLED`。
- 增加更细的操作审计，记录是谁更新了哪份知识、触发了哪次重建。
