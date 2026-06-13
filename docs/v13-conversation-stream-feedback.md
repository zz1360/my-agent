# v1.3 对话历史、流式输出与反馈闭环

v1.3 把轻量聊天页从“一次性问答页面”推进为“可持续使用的业务对话台”。核心目标是让客服或运营人员能连续对话、查看历史、追踪回答依据，并把人工反馈沉淀为后续评测和优化的数据。

## 新增能力

- 聊天页默认调用 `/api/agent/chat/stream`，通过 SSE 接收状态事件、回答增量和最终完整响应。
- `/api/agent/chat` 仍保持兼容，但响应新增 `messageId`，用于定位具体 Agent 回答。
- 新增会话历史接口，可以按租户和用户查看历史会话，并加载完整消息。
- 新增回答反馈接口，可以对某条 Agent 回答记录 `HELPFUL` 或 `NOT_HELPFUL`。
- 聊天页展示历史会话、流式输出开关、回答反馈按钮、引用、工具调用和审计 trace。

## 数据库变更

Flyway 新增 `V9__agent_conversation_history_feedback.sql`：

- `ai_agent_conversation`：保存会话摘要，包括标题、最近问题、最近 trace、风险等级和消息数。
- `ai_agent_message`：保存用户消息和 Agent 回答。Agent 回答会保存 trace、风险等级、置信度、引用 JSON 和工具调用 JSON。
- `ai_agent_message_feedback`：保存用户对 Agent 回答的反馈，后续可以进入评测集或 RAG 优化流程。

## 后端接口

- `POST /api/agent/chat`：同步问答，并落库会话和消息。
- `POST /api/agent/chat/stream`：SSE 流式问答。事件包括 `status`、`delta`、`complete`、`error`。
- `GET /api/agent/conversations`：查询当前用户历史会话。
- `GET /api/agent/conversations/{conversationId}`：查询会话详情和消息列表。
- `POST /api/agent/messages/{messageId}/feedback`：提交某条回答的有用性反馈。

## 实现说明

`AgentConversationService` 包装原有 `LogisticsAgentService`，负责请求归一化、调用 Agent、记录会话、记录消息、处理反馈和输出 SSE。

这样做的好处是：原来的业务编排、RAG、工具调用、权限、脱敏和审计逻辑不需要重写；v1.3 只在外围补齐对话产品需要的状态管理和反馈闭环。

当前 SSE 是应用层流式：后端先执行完整 Agent 编排，再把回答分块输出为 `delta` 事件，同时保留 `complete` 事件返回完整结构化响应。后续如果接入支持 token streaming 的 ChatClient，可以继续复用这个事件协议。

## 测试覆盖

`LogisticsAgentApplicationTests` 新增覆盖：

- V9 表结构迁移成功。
- 聊天后会话和消息可查询。
- Agent 回答返回 `messageId`。
- 回答反馈可以落库并返回反馈编号。
- SSE 接口返回 `status`、`delta`、`complete` 事件。

## 后续方向

- 把 `NOT_HELPFUL` 反馈自动转成评测样本候选。
- 将反馈原因和 RAG 实验台关联，用真实用户反馈驱动检索参数优化。
- 如果 DeepSeek ChatClient 启用真实流式能力，将 SSE `delta` 从应用层分块升级为模型 token 流。
- 在聊天页中把回答生成的动作草稿直接露出为操作按钮，进一步打通“问答 -> 建议 -> 执行”。
