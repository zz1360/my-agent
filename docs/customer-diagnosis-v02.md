# v0.2 客户异常诊断闭环

这一版新增专用接口：

```text
POST /api/agent/customer-diagnosis
```

目标是把“客户 C001 最近 30 天投诉为什么上升，是否满足赔付条件，下一步怎么处理？”做成一个完整闭环：

- 查云端 MySQL：客户画像、运单、异常、工单、诊断指标、SLA 规则。
- 查 PGVector：风险规则、延误 SOP、赔付政策、客服沟通规范等知识库片段。
- 生成结构化结果：异常归因、SLA/赔付候选、下一步动作、风险等级、引用来源。
- 写审计：trace、用户问题、最终回答、工具调用摘要和耗时。
- 启用 DeepSeek 时：模型基于后端结构化证据生成诊断叙述；失败时回退本地诊断。

## 请求示例

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

也可以传精确窗口：

```json
{
  "customerId": "C001",
  "from": "2026-05-06",
  "to": "2026-06-04"
}
```

## 响应重点字段

- `traceId`：本次诊断的审计 ID。
- `diagnosis`：窗口内运单数、异常数、投诉数、异常率、投诉率和主风险。
- `attributions`：按异常类型、责任归属、线路聚合的归因结果。
- `slaAssessments`：SLA/赔付候选运单。这里只给“候选/待复核/暂不满足”，不直接给最终赔付结论。
- `citations`：知识库引用，包含 `docId` 和 `chunkId`。
- `toolCalls`：后端工具调用摘要。
- `narrative`：面向客服/运营的诊断报告文本。
- `modelProvider`：`local-structured-diagnosis` 或 `deepseek`。

## 审计查询

按 trace 查询：

```bash
curl -s http://localhost:8080/api/agent/audit/{traceId}
```

按条件查询：

```bash
curl -s 'http://localhost:8080/api/agent/audit?tenantId=T001&customerId=C001&limit=20'
```

## 安全边界

- 模型不能直接查库，业务事实由后端工具查询。
- 涉及赔付、合同、责任认定时，只输出候选判断和人工复核建议。
- 工单里的 `compensationAmount` 不能描述为已支付金额，只能描述为登记金额、建议金额或待核算金额。
