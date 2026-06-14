# v1.9 质量运营闭环增强、评测对比与检索灰度

v1.9 接在 v1.8 的可编辑治理后台之后，重点把“告警能转任务”继续推进为“任务能处理、结果能复盘、检索能灰度对比”。

本版完成四件事：

- 告警任务支持状态流转、负责人和处理备注。
- 管理台新增治理趋势图，展示告警与任务处理走势。
- 评测运行支持列表查询和两次运行结果对比。
- 知识库搜索支持显式检索模式，提供 PGVector 灰度入口。

## 1. 告警任务流转

`logistics_ops_task` 新增字段：

- `owner_user_id`
- `last_comment`
- `updated_at`

新增接口：

```http
GET /api/agent/quality/alert-tasks
POST /api/agent/quality/alert-tasks/{taskId}/transition
```

支持的任务状态：

| 状态 | 含义 |
|---|---|
| `OPEN` | 已生成，等待处理 |
| `PROCESSING` | 已有人开始复核 |
| `RESOLVED` | 已处理完成 |
| `REJECTED` | 判断为不需要处理或误报 |

当任务进入 `RESOLVED` 或 `REJECTED` 后，系统会同步把对应 `ai_quality_alert` 标记为 `RESOLVED`。

这一步让质量告警真正进入运营处理链路，而不是只停留在列表里。

## 2. 治理趋势图

新增接口：

```http
GET /api/agent/quality/trends
```

返回内容包括：

- 每日新增告警数
- 每日恢复告警数
- 每日新增任务数
- 每日完成任务数
- 告警状态分布
- 任务状态分布

管理台新增“治理趋势图”，用轻量 SVG 折线展示告警打开、告警恢复和任务创建趋势，并用趋势行展示最近几天的打开/完成数量。

这个能力的价值是：可以看出质量治理是不是在变好。例如告警持续增加但任务完成数不增长，就说明运营处理能力或规则阈值可能需要调整。

## 3. 评测运行列表与版本对比

新增接口：

```http
GET /api/agent/evals/runs
GET /api/agent/evals/runs/compare?baselineRunId={runA}&candidateRunId={runB}
```

对比结果会按 case 输出：

- `UNCHANGED`：两次运行结果一致
- `IMPROVED`：基线失败、候选通过
- `REGRESSED`：基线通过、候选失败
- `NEW`：候选运行新增 case
- `REMOVED`：候选运行缺少 case

返回中还包括：

- 基线版本：模型 / 知识库 / 提示词
- 候选版本：模型 / 知识库 / 提示词
- 退化用例数
- 提升用例数
- 新增和移除用例数

这样就可以回答一个很关键的问题：

```text
这次换模型、换知识库或改提示词之后，到底有没有让 Agent 变好？
```

## 4. 检索模式灰度入口

`KnowledgeSearchOptions` 新增模式解析能力，支持：

| 模式 | 含义 |
|---|---|
| `keyword` / `KEYWORD_ONLY` | 只走关键词召回 |
| `vector` / `VECTOR_ONLY` | 只走 PGVector 向量召回 |
| `hybrid` / `HYBRID_RULE` | 向量 + 关键词 + 规则融合，不走 reranker |
| `hybrid_reranker` / `HYBRID_RERANKER` | 混合召回后再精排 |

知识库搜索接口新增 `mode` 参数：

```http
GET /api/knowledge/search?query=...&mode=hybrid_reranker
```

新增带分数明细的预览接口：

```http
GET /api/knowledge/search/preview?query=...&mode=keyword&topK=5
```

返回内容包括：

- 当前模式
- PGVector 是否 ready
- 召回片段
- 总分
- 向量分
- 关键词分
- 规则分
- reranker 分和 provider

这让后续真正切换 PGVector similarity search 时，可以先做灰度对比，而不是一次性替换线上检索链路。

## 管理台变化

`/admin/actions.html` 新增：

- “告警任务流转”：查看质量告警生成的运营任务，并支持处理中、解决、驳回。
- “治理趋势图”：查看告警打开、告警恢复和任务处理趋势。
- “评测结果版本对比”：选择两次评测运行，查看退化、提升和 case 变化。
- “检索模式灰度”：输入问题并选择 `keyword/vector/hybrid/hybrid_reranker` 预览召回。

## 数据库迁移

新增迁移文件：

```text
V15__quality_task_flow_eval_compare_retrieval_mode.sql
```

主要变化：

- 给 `logistics_ops_task` 增加任务处理字段。
- 新增质量任务查询索引。
- 默认评测集版本升级为 `v1.9`。

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
- 页面加载“告警任务流转”“治理趋势图”“评测结果版本对比”“检索模式灰度”。
- 检索预览使用 `hybrid_reranker` 命中冷链温控规范，显示 `vectorReady=false`。
- 评测集运行两次均通过 `5/5`。
- 两次评测运行对比结果：退化 0，提升 0，unchanged 5。
- 构造一条 `NOT_HELPFUL` 反馈后，评估出 `NEGATIVE_RATE` 告警。
- 告警转成 `task-alert-*` 任务后，任务可流转到 `PROCESSING` 和 `RESOLVED`。
- 任务解决后，治理趋势显示当日打开 1、完成 1。
- 浏览器控制台无 error 日志。

## 下一步建议

下一版可以考虑 v2.0：**质量运营报告与半自动修复建议**。

建议方向：

- 自动生成 Agent 质量周报。
- 对退化用例自动归因到模型、知识库、提示词或检索模式。
- 对质量告警给出候选修复动作，例如补知识、调阈值、加入评测集。
- 将 PGVector 检索模式接入 RAG 实验批量对比，形成线上切换依据。
