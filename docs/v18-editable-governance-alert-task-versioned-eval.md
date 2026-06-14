# v1.8 可编辑治理后台、告警任务化与评测版本追踪

v1.8 接在 v1.7 的质量告警、标签字典和评测集版本化之后，补齐“能在后台维护治理配置，并把质量问题转成任务”的能力。

本版完成四件事：

- 反馈标签字典可编辑。
- 质量告警规则可编辑。
- 质量告警可转运营任务。
- 评测运行记录模型、知识库和提示词版本。

## 1. 标签字典可编辑

新增请求 DTO：

- `FeedbackTagUpsertRequest`

新增接口：

```http
POST /api/agent/quality/feedback-tags/{tagCode}
```

能力：

- 新增标签。
- 修改标签名称、分类、说明、排序和启停状态。
- 候选标注时校验标签必须来自启用中的字典。

这个约束能避免运营人员自由输入标签，导致后续质量统计里出现很多同义、错别字或临时口径。

## 2. 告警规则可编辑

新增请求 DTO：

- `QualityAlertRuleUpsertRequest`

新增接口：

```http
POST /api/agent/quality/alert-rules/{ruleId}
```

当前支持的指标类型：

| 指标 | 含义 |
|---|---|
| `NEGATIVE_RATE` | 近 N 天负反馈率 |
| `REVIEW_BACKLOG` | 待审批或审批中的评测候选积压数 |
| `RAG_FAILURE_RATE` | 近 N 天 RAG 实验失败率 |

可维护字段包括：

- 规则名称
- 指标类型
- 阈值
- 窗口天数
- 告警级别
- 启停状态
- 说明

## 3. 告警转运营任务

`ai_quality_alert` 新增字段：

- `task_id`
- `task_created_at`

新增接口：

```http
POST /api/agent/quality/alerts/{alertId}/task
```

接口会复用已有业务模拟表 `logistics_ops_task`，为质量告警创建一条运营复核任务。

任务写入时使用：

- `customer_id = QUALITY`
- `owner_role = OPS_MANAGER`
- `status = OPEN`
- `action_id = quality-alert-{alertId}`

这样质量告警不只停留在看板，而是进入“有人处理”的任务流。

## 4. 评测运行版本追踪

`ai_eval_run` 新增字段：

- `model_version`
- `knowledge_version`
- `prompt_version`

评测接口新增可选参数：

```http
POST /api/agent/evals/run?modelVersion=deepseek-v4-flash&knowledgeVersion=kb-v1.8&promptVersion=prompt-v1.8
POST /api/agent/evals/suites/{suiteId}/run?modelVersion=deepseek-v4-flash&knowledgeVersion=kb-v1.8&promptVersion=prompt-v1.8
```

返回的 `EvalRunResponse` 会带回这些版本字段。

默认评测集版本同步升级为：

```text
suite-logistics-regression
version: v1.8
```

这个改动让评测结果能回答一个更工程化的问题：同一套回归集下，某个模型版本、知识库版本和提示词版本是否真的变好了。

## 管理台变化

`/admin/actions.html` 新增：

- “标签字典维护”表单：保存标签编码、名称、分类、排序、启停和说明。
- “告警规则维护”表单：保存规则 ID、指标、阈值、窗口、级别、启停和说明。
- 告警列表“转任务”按钮：创建运营复核任务后回显任务 ID，并禁用重复创建。
- “评测运行版本信息”表单：运行评测集时带上模型、知识库、提示词版本。

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

浏览器验证：

- 临时启动 `http://127.0.0.1:18080/admin/actions.html`。
- 页面加载 v1.8 三个新区域：标签字典维护、告警规则维护、评测运行版本信息。
- 保存 `RAG_STABILITY` 标签后，标签字典从 5 个变为 6 个。
- 构造一条 `NOT_HELPFUL` 反馈后，点击“评估规则”生成 `NEGATIVE_RATE` 告警。
- 点击“转任务”后生成 `task-alert-*` 运营任务，并回写到告警行。
- 点击“运行”默认评测集，返回 `PASSED`、`5/5`，并显示 `kb-v1.8`。
- 浏览器控制台无 error 日志。

## 下一步建议

下一版可以考虑 v1.9：**评测结果对比与质量运营报告**。

建议方向：

- 增加评测运行列表和详情页。
- 支持按模型版本、知识库版本、提示词版本对比通过率和失败用例。
- 给质量告警任务增加处理状态、负责人和处理备注。
- 将负反馈、告警、任务、评测结果串成一份自动质量周报。
