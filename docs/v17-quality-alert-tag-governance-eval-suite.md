# v1.7 质量告警、标签治理与评测集版本化

v1.7 在 v1.6 的“质量趋势 + 候选审计”基础上，继续补齐 Agent 质量运营体系的四个能力：

- 反馈标签字典化
- 质量告警规则
- 候选审计字段级 diff
- 评测集版本化

## 1. 反馈标签字典化

新增表：

- `ai_feedback_tag_dictionary`

默认内置标签：

| 标签 | 含义 |
|---|---|
| `RAG_QUALITY` | 检索或引用质量问题 |
| `POLICY_GAP` | 制度知识缺口 |
| `TOOL_OR_DATA` | 工具或业务数据问题 |
| `ACTION_SAFETY` | 自动化动作安全问题 |
| `ANSWER_QUALITY` | 回答表达、结构或推理质量问题 |

新增接口：

```http
GET /api/agent/quality/feedback-tags
```

管理台会展示“反馈标签字典”，候选详情里也可以从字典一键追加标准标签。

## 2. 质量告警规则

新增表：

- `ai_quality_alert_rule`
- `ai_quality_alert`

默认规则：

| 规则 | 指标 | 阈值 |
|---|---|---|
| `qa-negative-rate-7d` | 近 7 天负反馈率 | `0.2` |
| `qa-review-backlog` | 待审批候选积压数 | `5` |
| `qa-rag-failure-rate-7d` | 近 7 天 RAG 实验失败率 | `0.3` |

新增接口：

```http
GET /api/agent/quality/alert-rules
GET /api/agent/quality/alerts
POST /api/agent/quality/alerts/evaluate
```

规则评估逻辑：

- 指标超过阈值：创建或更新 `OPEN` 告警。
- 指标恢复正常：把该规则的 `OPEN` 告警更新为 `RESOLVED`。

这样系统从“被动看指标”前进到“主动发现质量风险”。

## 3. 候选审计字段级 diff

v1.6 已经有候选操作审计，但详情只记录动作摘要。

v1.7 开始，以下动作的 `detail_json` 会包含字段级变化：

- 标注候选
- 审批候选
- 创建 RAG 实验
- 转正式评测

结构包括：

```json
{
  "changedFields": {
    "reviewStatus": {
      "before": "UNREVIEWED",
      "after": "APPROVED"
    }
  },
  "before": {},
  "after": {}
}
```

这让审计不只知道“谁操作了”，还能知道“改了哪些关键字段”。

## 4. 评测集版本化

新增表：

- `ai_eval_suite`
- `ai_eval_suite_case`

`ai_eval_run` 新增字段：

- `suite_id`
- `suite_version`

默认评测集：

```text
suite-logistics-regression
version: v1.7
```

新增接口：

```http
GET /api/agent/evals/suites
POST /api/agent/evals/suites/{suiteId}/run
```

价值：

- 可以把评测用例按版本成组管理。
- 可以比较不同模型、知识库版本、提示词版本在同一套评测集上的表现。
- 后续可以扩展出 `v1.8`、`v2.0` 等更复杂的评测套件。

## 管理台变化

`/admin/actions.html` 新增：

- “反馈标签字典”
- “质量告警”
- “评测集版本”
- 候选标注区支持从字典追加标签

## 验证

本版已通过：

```bash
mvn -Dmaven.repo.local=/Users/zhangzhuang/Documents/develop/maven_repository test
```

结果：

- `Tests run: 20`
- `Failures: 0`
- `Errors: 0`
- `Skipped: 2`

测试覆盖：

- Flyway 可迁移到 v13。
- 管理台包含标签字典、质量告警和评测集版本入口。
- 标签字典接口返回默认标签。
- 质量告警规则可评估并生成 `OPEN` 告警。
- 候选审批审计记录包含 `changedFields`、`before`、`after`。
- 默认评测集可查询，并可按评测集版本运行。

## 下一步建议

- 把标签字典和告警规则做成可编辑页面。
- 告警命中后自动生成运营复核任务。
- 给质量趋势加折线图。
- 将评测集版本和知识库版本、模型版本关联起来。
