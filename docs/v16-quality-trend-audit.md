# v1.6 质量趋势与候选审计

v1.6 在 v1.5 的“反馈 -> 候选 -> 标注 -> 审批 -> RAG 实验/正式评测”链路上补了两个生产化能力：

- 质量趋势：反馈质量看板支持日期窗口，并返回按天聚合的趋势数据。
- 候选审计：关键候选操作写入审计表，能追踪谁在什么时候做了什么。

## 数据库变化

新增 Flyway 脚本：

- `V12__feedback_candidate_audit_trend.sql`

新增表：

- `ai_eval_case_candidate_audit`

核心字段：

| 字段 | 作用 |
|---|---|
| `audit_id` | 审计记录 ID |
| `candidate_id` | 关联的评测候选 |
| `feedback_id` | 候选来源反馈 |
| `action_type` | 操作类型 |
| `actor_id` | 操作人 |
| `review_status` | 操作后的审批状态 |
| `summary` | 面向运营的摘要 |
| `detail_json` | 操作细节 JSON |
| `created_at` | 操作时间 |

当前写入的 `action_type` 包括：

- `CANDIDATE_CREATED`
- `CANDIDATE_ANNOTATED`
- `CANDIDATE_APPROVED`
- `CANDIDATE_REJECTED`
- `CANDIDATE_REVIEW_UPDATED`
- `RAG_EXPERIMENT_CREATED`
- `EVAL_CASE_PROMOTED`

## 后端能力

### 质量指标支持时间窗口

`GET /api/agent/feedback/quality-metrics`

新增可选参数：

- `from`：开始日期，格式 `yyyy-MM-dd`
- `to`：结束日期，格式 `yyyy-MM-dd`

不传时默认查询最近 30 天。时间范围最多支持 180 天。

响应新增字段：

- `fromDate`
- `toDate`
- `dailyTrends`

`dailyTrends` 每天返回：

| 字段 | 含义 |
|---|---|
| `date` | 日期 |
| `totalFeedback` | 当天反馈总数 |
| `notHelpfulFeedback` | 当天负反馈数 |
| `candidateCount` | 当天创建的候选数 |
| `approvedCandidates` | 当天创建且当前已审批通过的候选数 |
| `ragExperimentCandidates` | 当天创建且当前已创建 RAG 实验的候选数 |
| `negativeRate` | 负反馈率 |
| `candidateConversionRate` | 候选转化率 |
| `approvedCandidateRate` | 候选通过率 |

### 新增候选审计查询

`GET /api/agent/eval-candidate-audits`

可选参数：

- `candidateId`
- `actionType`
- `limit`

该接口用于查询候选生命周期审计记录，管理台默认展示最近 30 条。

## 管理台变化

`/admin/actions.html` 增加：

- “质量趋势”面板：展示最近有数据日期的负反馈率和候选转化率。
- “候选操作审计”面板：展示候选创建、标注、审批、创建 RAG 实验和转评测记录。
- 候选生命周期动作完成后自动刷新质量看板、趋势和审计列表。

## 为什么要做趋势和审计

只看当前汇总值，很难判断系统质量是变好了还是变差了。例如负反馈率今天突然升高，需要结合最近几天的趋势才能判断是不是某次知识库发布、模型切换或业务规则变更导致。

审计日志解决的是另一个问题：反馈数据进入评测体系后，需要知道它经历了哪些人工处理。这样可以回答：

- 这个评测用例来自哪条用户反馈？
- 谁标注了期望引用和关键词？
- 谁审批通过或驳回？
- 什么时候创建了 RAG 实验？
- 什么时候沉淀成正式评测用例？

对于 Agent 项目来说，这相当于把“用户反馈”升级成“可追溯的质量资产”。

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

覆盖点：

- Flyway 可迁移到 v12。
- 管理台页面包含质量趋势和候选操作审计。
- 负反馈可生成候选。
- 候选标注、审批、RAG 实验、转评测均可写入审计。
- 质量指标返回 `fromDate`、`toDate` 和 `dailyTrends`。

## 下一步建议

- 给 `dailyTrends` 增加前端折线图或面积图。
- 将 `action_type` 和反馈标签做成可配置字典。
- 审计详情中记录字段级 diff。
- 增加质量告警，例如近 7 天负反馈率超过阈值时提醒运营复核。
