# v1.5 评测候选标注、审批与质量看板

v1.5 在 v1.4 的反馈学习闭环上继续补齐“人工确认”这一层。负反馈可以自动生成候选，但候选能否进入正式评测，需要有人确认它的期望引用、期望关键词、反馈标签和审批状态。

## 本版目标

- 给评测候选增加人工标注能力。
- 给候选增加审批状态，区分 `UNREVIEWED`、`REVIEWING`、`APPROVED`、`REJECTED`。
- 只有审批通过的候选，才能启用为正式评测用例。
- 增加反馈标签，支持把问题归类为 RAG 质量、工具/数据、动作安全、回答质量等。
- 管理台增加反馈质量看板，展示负反馈率、候选转化率、候选通过率和 RAG 实验通过率。

## 数据库变化

新增 Flyway 脚本：

- `V11__feedback_candidate_annotation_metrics.sql`

`ai_eval_case_candidate` 增加字段：

| 字段 | 作用 |
|---|---|
| `feedback_tags` | 反馈标签，多值用换行保存 |
| `annotation_note` | 人工标注备注 |
| `review_status` | 审批状态 |
| `reviewer_id` | 审批人 |
| `review_comment` | 审批意见 |
| `reviewed_at` | 审批时间 |
| `annotated_by` | 标注人 |
| `annotated_at` | 标注时间 |

## 后端能力

新增接口：

- `POST /api/agent/eval-candidates/{candidateId}/annotate`：保存候选标注，支持修改评测类型、期望关键词、期望引用、RAG 查询和反馈标签。
- `POST /api/agent/eval-candidates/{candidateId}/review`：审批候选，可设置为 `APPROVED` 或 `REJECTED`。
- `GET /api/agent/feedback/quality-metrics`：查看反馈质量指标。

新增 DTO：

- `EvalCaseCandidateAnnotateRequest`
- `EvalCaseCandidateReviewRequest`
- `FeedbackQualityMetricsResponse`

## 质量指标

质量看板目前返回这些指标：

| 指标 | 含义 |
|---|---|
| `negativeRate` | 负反馈数 / 总反馈数 |
| `candidateConversionRate` | 候选数 / 负反馈数 |
| `approvedCandidateRate` | 审批通过候选数 / 候选数 |
| `ragExperimentPassRate` | RAG 实验通过 run 数 / RAG 实验 run 总数 |
| `evalPassRate` | 由反馈候选生成的评测结果通过数 / 结果总数 |
| `byReason` | 按反馈原因统计 |
| `byTag` | 按反馈标签统计 |
| `byReviewStatus` | 按候选审批状态统计 |
| `ragExperimentStatus` | 按 RAG 实验运行状态统计 |
| `evalRunStatus` | 按评测结果通过/失败统计 |

## 管理台变化

`/admin/actions.html` 增加：

- “反馈质量看板”面板。
- 候选列表展示审批状态和反馈标签。
- 候选详情区增加可编辑表单。
- 支持保存标注、审批通过、驳回。
- “转评测”现在默认创建启用状态的正式评测用例，因此需要先审批通过。

## 为什么要做标注和审批

用户反馈是很宝贵的数据，但它不一定天然准确。

例如用户点了“无帮助”，原因可能是：

- 检索没有命中正确制度。
- 回答没有正确使用引用。
- 工具数据不完整。
- 用户问题本身不清楚。
- 权限限制导致看不到完整数据。
- 回答话术不适合业务场景。

所以 v1.5 引入了一个更适合生产的链路：

```text
负反馈
  -> 自动生成候选
  -> 人工标注期望
  -> 审批通过
  -> 启用为正式评测
```

这能避免把噪声反馈直接加入回归集，也让每一个评测用例都有明确的业务依据。

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

浏览器页面验证：

- `/chat.html` 可完成 C001 问答。
- 可提交 `NOT_HELPFUL / CITATION_WEAK` 反馈。
- 可从回答生成动作草稿。
- `/admin/actions.html` 可将反馈转成候选。
- 候选详情可保存标注。
- 候选可审批通过。
- 审批通过后可创建 RAG 实验并转为正式评测用例。
- 反馈质量看板展示 100% 负反馈率、100% 候选转化率、100% 候选通过率和 100% RAG 实验通过率。
- 浏览器控制台无错误日志。

## 下一步建议

- 给候选标注做更完整的弹窗或独立页面，支持富文本和引用选择器。
- 给质量看板增加时间趋势，例如近 7 天、近 30 天。
- 将反馈标签沉淀为可配置字典，避免运营人员自由输入造成统计分散。
- 将审批动作纳入审计日志，记录审批前后的字段差异。
- 把质量指标接入告警，例如负反馈率超过阈值时提醒运营复核。
