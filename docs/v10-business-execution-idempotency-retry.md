# v1.0 真实业务执行闭环、幂等与重试

v1.0 在 v0.9 的动作执行适配器基础上，补上更接近真实后端系统的落地能力：执行器不再只返回模拟 JSON，而是写入本项目的业务结果表，并在执行日志中记录幂等键、外部业务 ID 和重试信息。

## 核心变化

- 新增 `V8__action_business_execution_idempotency.sql`。
- `ai_agent_action_execution` 增加：
  - `external_ref_id`
  - `idempotency_key`
  - `next_retry_at`
  - `max_retry_count`
- 新增业务落地表：
  - `logistics_ticket_note`
  - `logistics_ops_task`
  - `logistics_customer_reply_draft`
  - `logistics_compensation_review_task`
- 执行器写入真实业务表，返回业务表 ID。
- 执行接口支持 `idempotencyKey`，重复执行不会重复创建业务记录。
- 新增失败执行重试接口。

## 业务表落地

v0.9 的执行器只模拟目标系统返回，v1.0 开始写入项目内的业务表：

| 动作类型 | 业务表 | 说明 |
|---|---|---|
| `TICKET_NOTE` | `logistics_ticket_note` | 保存工单备注草稿 |
| `OPERATIONS_FOLLOW_UP` | `logistics_ops_task` | 创建运营复盘任务 |
| `CUSTOMER_REPLY` | `logistics_customer_reply_draft` | 保存客户回复草稿，不自动发送 |
| `COMPENSATION_REVIEW` | `logistics_compensation_review_task` | 创建赔付复核任务，不直接赔付 |

这些表仍然是本项目内的模拟业务系统，但已经具备真实后端落地形态：有业务主键、动作关联、状态、创建人和创建时间。

## 幂等执行

执行请求可以传入：

```json
{
  "idempotencyKey": "ticket-note-C001-20260604"
}
```

系统会把它和动作、执行器一起组成最终幂等键：

```text
actionId + executorName + request.idempotencyKey
```

如果同一个动作用同一个幂等键重复执行：

- 不会重复写入业务表。
- 会返回已有成功执行记录。
- 动作保持 `APPLIED`。

如果调用方不传 `idempotencyKey`，系统会使用 `default`，同一个动作默认也只落地一次。

## 失败重试

执行失败时，系统会写入 `FAILED` 执行记录，并保存：

- `failure_reason`
- `retry_count`
- `next_retry_at`
- `max_retry_count`

重试接口：

```bash
curl -s http://localhost:8080/api/agent/actions/executions/exec-xxxxxxxxxxxx/retry \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "T001",
    "userId": "u-ops-001",
    "roles": ["OPS_MANAGER"],
    "comment": "外部系统恢复后重试"
  }'
```

重试成功后：

- 新增一条 `SUCCESS` 执行日志。
- 业务表只写一条业务结果。
- 动作状态更新为 `APPLIED`。

## 高风险动作仍不自动化

v1.0 没有放开赔付或客户消息自动执行。

`COMPENSATION_REVIEW` 即使执行成功，也只是写入：

```text
logistics_compensation_review_task
```

并且：

```text
payment_created = false
```

`CUSTOMER_REPLY` 也只是写入回复草稿：

```text
logistics_customer_reply_draft
```

不会真正发送客户消息。

## API

执行单个动作，支持幂等键：

```bash
curl -s http://localhost:8080/api/agent/actions/act-ticket-20260604-xxxxxxxx/execute \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "T001",
    "userId": "u-ops-001",
    "roles": ["OPS_MANAGER"],
    "idempotencyKey": "ticket-note-C001-20260604",
    "comment": "保存内部工单备注草稿"
  }'
```

重试失败执行：

```bash
curl -s http://localhost:8080/api/agent/actions/executions/exec-xxxxxxxxxxxx/retry \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "T001",
    "userId": "u-ops-001",
    "roles": ["OPS_MANAGER"],
    "comment": "外部系统恢复后重试"
  }'
```

查询执行日志：

```bash
curl -s 'http://localhost:8080/api/agent/actions/act-ticket-20260604-xxxxxxxx/executions?tenantId=T001&roles=OPS_MANAGER'
```

## 测试覆盖

v1.0 新增和加强的测试覆盖：

- Flyway v8 迁移执行。
- 新业务表可创建。
- 低风险动作执行后写入 `logistics_ticket_note`。
- 高风险赔付复核动作写入 `logistics_compensation_review_task`，且不直接赔付。
- 模拟执行失败会记录 `FAILED` 和 `next_retry_at`。
- 失败执行可以通过 retry 接口重试成功。
- 同一幂等键重复执行不会重复创建业务任务。

## 下一步

下一版可以考虑 v1.1：动作执行可观测性与管理后台。

建议补：

- 执行日志搜索接口，按状态、执行器、目标系统过滤。
- 失败执行的重试队列视图。
- 业务表回链，从工单备注/运营任务跳回 Agent 动作。
- 执行耗时、失败率、重试成功率指标。
- 更严格的幂等冲突提示，例如同一幂等键用于不同动作时直接拒绝。
