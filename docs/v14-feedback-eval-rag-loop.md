# v1.4 反馈样本池、评测候选与 RAG 实验闭环

v1.4 把 v1.3 已经落库的回答反馈继续往后推进：`NOT_HELPFUL` 反馈不再只是一个记录，而是可以进入反馈样本池，转成评测候选，再生成 RAG 实验或正式评测用例。

## 本版目标

- 让客服或运营在聊天页提交的负反馈，可以被运营台集中查看。
- 把“引用不准、回答不可用”等人工反馈转成可复现的评测候选。
- 对 `CITATION_WEAK` 类反馈自动倾向生成 RAG 类型候选，便于验证检索命中。
- 支持从候选一键创建 RAG 实验，并可立即运行 `KEYWORD_ONLY`、`HYBRID_RULE`、`HYBRID_RERANKER` 对比。
- 支持从候选沉淀正式评测用例，默认禁用，避免未经确认的用户反馈直接影响 CI 评测。
- 聊天页增加“生成动作草稿”入口，让当前回答可以直接进入动作复核流程。

## 数据库变化

新增 Flyway 脚本：

- `V10__feedback_eval_candidate_loop.sql`

新增表：

- `ai_eval_case_candidate`

这个表用于保存从反馈样本抽取出来的评测候选。它记录了原始反馈、会话、消息、trace、问题、回答、评测类型、期望关键词、期望引用、RAG 查询、候选状态、关联评测用例和关联 RAG 实验。

状态流转可以理解为：

```text
NOT_HELPFUL feedback
  -> CANDIDATE
  -> RAG_EXPERIMENT_CREATED
  -> EVAL_CASE_CREATED
```

## 后端能力

新增 `FeedbackLearningService`，负责反馈学习闭环：

- 查询反馈样本池。
- 将负反馈转成评测候选。
- 根据反馈原因推断候选类型。
- 从候选创建 RAG 实验并运行。
- 从候选创建正式评测用例。

权限边界上，普通客服可以提交和查看自己的反馈；评测候选、RAG 实验和正式评测用例的维护限定在 `ADMIN`、`OPS_MANAGER`、`OPERATIONS` 角色。

新增接口：

- `GET /api/agent/feedback`：查询反馈样本池，默认只看 `NOT_HELPFUL`。
- `GET /api/agent/eval-candidates`：查询评测候选。
- `POST /api/agent/feedback/{feedbackId}/eval-candidate`：将反馈转成评测候选。
- `POST /api/agent/eval-candidates/{candidateId}/rag-experiment`：由候选创建 RAG 实验，可立即运行。
- `POST /api/agent/eval-candidates/{candidateId}/promote`：由候选创建正式评测用例。

## 前端变化

聊天页 `/chat.html`：

- 回答详情中新增“生成动作草稿”按钮。
- 页面会从当前问题或回答中识别 `C001` 这类客户编号。
- 调用 `/api/agent/actions/from-diagnosis` 生成客户回复、工单备注、赔付复核、运营跟进等动作草稿。
- 原有有用性反馈继续保留。

管理台 `/admin/actions.html`：

- 新增“反馈样本池”面板，可以查看负反馈并一键转候选。
- 新增“评测候选”面板，可以查看候选状态、运行 RAG 实验、转正式评测用例。
- 右下角“业务回链”区域复用为反馈详情和候选详情展示区。

## Agent 开发知识点

这一版体现了一个比较重要的 Agent 工程思路：用户反馈不是简单存表，而是要进入可验证的改进闭环。

常见链路是：

```text
用户问题
  -> Agent 回答
  -> 人工反馈
  -> 反馈样本池
  -> 评测候选
  -> RAG 实验 / 正式评测用例
  -> 下一轮提示词、检索、工具或知识库优化
```

这样做有几个好处：

- 可以知道真实用户觉得哪里不好。
- 可以把主观反馈转成可重复运行的评测样本。
- 可以区分是回答生成问题、工具查询问题，还是 RAG 检索问题。
- 可以用 RAG 实验对比不同召回和 rerank 策略，而不是凭感觉调参。
- 可以避免把未经人工确认的差评直接放进主评测集。

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
- 可从当前回答生成 4 条动作草稿。
- `/admin/actions.html` 可看到 1 条反馈样本和 1 条评测候选。
- 候选可创建并运行 RAG 实验。
- 候选可转成默认禁用的正式评测用例。
- 浏览器控制台无错误日志。

## 下一步建议

- 给评测候选增加人工编辑页面，允许运营修改期望引用、期望关键词和评测类型。
- 给反馈样本打标签，例如“检索问题”“工具问题”“话术问题”“权限问题”。
- 把候选提升评测用例前增加审批状态，避免误加入评测集。
- 统计反馈转化率、RAG 实验通过率和评测用例命中率，形成 Agent 质量看板。
