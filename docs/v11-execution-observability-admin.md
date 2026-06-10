# v1.1 执行可观测性与管理后台接口

v1.1 在 v1.0 的业务表落地、幂等和重试基础上，补上管理后台最需要的观测能力：执行日志搜索、失败重试队列、执行指标和业务回链。

## 核心变化

- 新增执行日志搜索接口，可以按状态、动作类型、执行器、目标系统和时间范围过滤。
- 新增失败重试队列接口，方便运营或系统任务找到可重试的失败执行。
- 新增执行指标接口，返回总执行数、成功数、失败数、可重试失败数和分组成功率。
- 新增动作业务回链接口，可以从 Agent 动作跳到实际写入的业务表记录。
- 新增集成测试覆盖搜索、重试队列、指标和业务回链。

## API

搜索执行日志：

```bash
curl -s 'http://localhost:8080/api/agent/actions/executions?tenantId=T001&roles=OPS_MANAGER&status=SUCCESS&actionType=OPERATIONS_FOLLOW_UP&limit=20'
```

查看失败重试队列：

```bash
curl -s 'http://localhost:8080/api/agent/actions/executions/retry-queue?tenantId=T001&roles=OPS_MANAGER&dueOnly=false&limit=20'
```

查看执行指标：

```bash
curl -s 'http://localhost:8080/api/agent/actions/executions/metrics?tenantId=T001&roles=OPS_MANAGER'
```

查看动作业务回链：

```bash
curl -s 'http://localhost:8080/api/agent/actions/act-ops-20260604-xxxxxxxx/business-link?tenantId=T001&roles=OPS_MANAGER'
```

## 指标说明

执行指标返回：

| 字段 | 说明 |
|---|---|
| `totalExecutions` | 执行总次数 |
| `successCount` | 成功次数 |
| `failedCount` | 失败次数 |
| `retryableFailedCount` | 仍可重试的失败次数 |
| `successRate` | 成功率 |
| `failureRate` | 失败率 |
| `byActionType` | 按动作类型分组 |
| `byExecutor` | 按执行器分组 |
| `byTargetSystem` | 按目标系统分组 |

这些指标可以帮助判断：

- 哪类动作失败最多。
- 哪个执行器最不稳定。
- 低风险自动化是否稳定。
- 重试队列是否堆积。

## 业务回链

业务回链接口根据动作类型查询对应业务表：

| 动作类型 | 业务表 |
|---|---|
| `TICKET_NOTE` | `logistics_ticket_note` |
| `OPERATIONS_FOLLOW_UP` | `logistics_ops_task` |
| `CUSTOMER_REPLY` | `logistics_customer_reply_draft` |
| `COMPENSATION_REVIEW` | `logistics_compensation_review_task` |

返回字段包括：

- `businessTable`
- `businessId`
- `customerId`
- `waybillId`
- `status`
- `traceId`
- `latestExecutionId`

这样管理后台可以从 Agent 动作跳到业务结果，也能从业务结果回溯到 Agent trace。

## 测试覆盖

v1.1 复用动作执行测试场景，新增断言：

- `GET /api/agent/actions/executions` 能按状态、动作类型和目标系统搜索。
- `GET /api/agent/actions/executions/retry-queue` 能查到失败且可重试的执行记录。
- `GET /api/agent/actions/executions/metrics` 能返回成功、失败、可重试失败和分组指标。
- `GET /api/agent/actions/{actionId}/business-link` 能回链到实际业务表记录。

## 下一步

下一版可以做 v1.2：轻量管理页面。

建议把这些 API 做成一个简单页面：

- 动作列表。
- 执行日志列表。
- 失败重试队列。
- 执行指标看板。
- 业务回链详情。

这样系统就不只是后端接口，而是更接近业务人员可操作的 Agent 管理台。
