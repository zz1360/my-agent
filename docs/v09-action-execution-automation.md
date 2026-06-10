# v0.9 动作执行适配器与低风险自动化

v0.9 接在 v0.8 的动作草稿和人工复核之后，补上“审批后如何安全执行”的一层。当前版本仍然不对接真实工单、CRM 或赔付系统，而是用本地模拟适配器验证架构和状态流。

## 核心变化

- 新增 `ai_agent_action_execution` 表，记录每次动作执行的输入、输出、目标系统、执行人和结果。
- 新增 `AgentActionExecutor` 执行器接口，每类动作可以接不同外部系统。
- 新增 4 个模拟执行器：
  - `TicketNoteActionExecutor`
  - `OperationsFollowUpActionExecutor`
  - `CustomerReplyDraftActionExecutor`
  - `CompensationReviewActionExecutor`
- 新增低风险自动执行接口，只自动执行内部工单备注和运营复盘类动作。
- 高风险或客户触达动作需要人工审批后再使用 `force=true` 手动触发。

## 为什么需要执行适配器

Agent 生成的动作草稿和真实业务系统之间最好隔一层适配器：

```text
Agent 动作草稿 -> 执行适配器 -> 工单/CRM/赔付/任务中心
```

这样做有几个好处：

- Agent 不需要知道外部系统 API 的细节。
- 不同动作类型可以独立对接不同系统。
- 执行请求和返回结果可以统一落库。
- 失败、重试、幂等、权限都可以集中处理。

## 当前执行策略

当前动作类型的执行策略如下：

| 动作类型 | 执行器 | 目标系统 | 是否低风险自动执行 |
|---|---|---|---|
| `TICKET_NOTE` | `ticket-note-draft-executor` | `SIMULATED_TICKET_SYSTEM` | 是 |
| `OPERATIONS_FOLLOW_UP` | `operations-follow-up-task-executor` | `SIMULATED_OPS_TASK_CENTER` | 是 |
| `CUSTOMER_REPLY` | `customer-reply-draft-executor` | `SIMULATED_CRM_REPLY_DRAFT` | 否 |
| `COMPENSATION_REVIEW` | `compensation-review-queue-executor` | `SIMULATED_COMPENSATION_REVIEW_QUEUE` | 否 |

注意：`COMPENSATION_REVIEW` 即使手动执行，也只是创建“赔付复核队列项”，不会直接生成赔付金额或付款记录。

## API

手动执行单个已审批动作：

```bash
curl -s http://localhost:8080/api/agent/actions/act-ticket-20260604-xxxxxxxx/execute \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "T001",
    "userId": "u-ops-001",
    "roles": ["OPS_MANAGER"],
    "comment": "保存内部工单备注草稿"
  }'
```

手动触发高风险动作，需要显式 `force=true`：

```bash
curl -s http://localhost:8080/api/agent/actions/act-comp-20260604-xxxxxxxx/execute \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "T001",
    "userId": "u-ops-001",
    "roles": ["OPS_MANAGER"],
    "force": true,
    "comment": "人工确认只创建赔付复核队列，不直接赔付"
  }'
```

自动执行客户维度的低风险已审批动作：

```bash
curl -s http://localhost:8080/api/agent/actions/automation/run \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "T001",
    "userId": "u-ops-001",
    "roles": ["OPS_MANAGER"],
    "customerId": "C001",
    "limit": 20
  }'
```

查看某个动作的执行日志：

```bash
curl -s 'http://localhost:8080/api/agent/actions/act-ticket-20260604-xxxxxxxx/executions?tenantId=T001&roles=OPS_MANAGER'
```

## 状态变化

执行前：

```text
PENDING_REVIEW -> APPROVED
```

执行成功后：

```text
APPROVED -> APPLIED
```

执行日志会记录到：

```text
ai_agent_action_execution
```

如果执行失败，会保存 `FAILED` 执行记录和失败原因，动作草稿不会被标记为 `APPLIED`。

## 测试覆盖

v0.9 新增集成测试覆盖：

- Flyway v7 迁移执行。
- 低风险动作自动执行。
- 高风险赔付复核动作不会被自动执行。
- 高风险动作不带 `force=true` 会被拒绝。
- 高风险动作带 `force=true` 只进入模拟赔付复核队列。
- 执行日志可按动作查询。

## 下一步

真实项目里，下一步可以把模拟执行器替换为真实适配器：

- 工单系统：保存备注草稿、创建待办、更新工单状态。
- CRM：保存客户回复草稿，但仍由客服点击发送。
- 赔付系统：创建复核申请，不直接生成付款。
- 任务中心：创建运营复盘任务并分配负责人。

当对接真实系统后，建议继续补上幂等键、重试策略、外部错误码映射和执行超时告警。
