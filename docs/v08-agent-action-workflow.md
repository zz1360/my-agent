# v0.8 Agent 动作草稿与人工复核

v0.8 把客户异常诊断从“给出建议”推进到“生成可复核的业务动作草稿”。Agent 仍然不直接修改真实业务数据，而是把证据、建议文本和风险等级保存到动作草稿表，由人工审批后再执行。

## 核心变化

- 新增 `ai_agent_action_draft` 表，保存动作草稿、证据 JSON、复核状态和复核人。
- 新增 `AgentActionService`，基于客户诊断窗口重新查询客户、运单、异常和工单证据。
- 新增 `POST /api/agent/actions/from-diagnosis`，从一次诊断 trace 生成后续动作建议。
- 新增动作复核接口，支持 `APPROVED`、`REJECTED`、`APPLIED` 状态。
- 新增集成测试，覆盖诊断、动作生成、人工审批和状态查询。

## 为什么不让 Agent 直接写业务表

物流场景里的动作通常涉及客户承诺、赔付、工单备注和运营责任归属，这些都可能产生实际成本或合规风险。v0.8 采用“AI 生成草稿，人类复核执行”的方式：

- Agent 负责整理证据、生成话术和建议动作。
- 人工复核人负责判断证据是否充分、结论是否能落地。
- 系统保留 traceId、动作草稿和 evidenceJson，后续可以审计。

这也是企业级 Agent 常见的落地路径：先做可审计建议，再逐步开放低风险自动化。

## 动作类型

当前会生成这些动作草稿：

- `CUSTOMER_REPLY`：客户回复话术，适合客服对客沟通。
- `TICKET_NOTE`：工单备注草稿，适合补充到投诉或异常工单。
- `COMPENSATION_REVIEW`：赔付复核任务，提醒人工核对 SLA、责任归属和签收时间。
- `OPERATIONS_FOLLOW_UP`：运营复盘动作，适合异常率或投诉量较高的客户。

## API 示例

先调用客户诊断接口拿到 `traceId`：

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

再从诊断 trace 生成动作草稿：

```bash
curl -s http://localhost:8080/api/agent/actions/from-diagnosis \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "T001",
    "userId": "u-cs-001",
    "roles": ["CUSTOMER_SERVICE"],
    "traceId": "trace-20260604-xxxxxxxx",
    "conversationId": "conv-actions-001",
    "customerId": "C001",
    "days": 30
  }'
```

运营经理审批某个动作草稿：

```bash
curl -s http://localhost:8080/api/agent/actions/act-comp-20260604-xxxxxxxx/review \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "T001",
    "userId": "u-ops-001",
    "roles": ["OPS_MANAGER"],
    "status": "APPROVED",
    "comment": "证据完整，进入人工赔付核算。"
  }'
```

查询动作草稿：

```bash
curl -s 'http://localhost:8080/api/agent/actions?tenantId=T001&roles=CUSTOMER_SERVICE&customerId=C001&status=APPROVED&limit=10'
```

## 下一步可扩展点

- 将 `APPLIED` 状态对接真实工单、赔付或 CRM 系统。
- 给每类动作增加模板版本管理，避免话术和业务口径散落在代码里。
- 把动作审批纳入评测，检查动作是否包含必要证据、是否避免越权承诺。
- 对高风险动作增加双人复核或金额阈值规则。
