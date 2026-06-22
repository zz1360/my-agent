# v2.3 前后端分离第一阶段

本阶段完成了三件事：建立 Vue 工程、迁移聊天页、搭建模块化管理台框架。

## 目录

当前采用渐进式单仓库结构：

```text
superAgent/
├── src/                 # Spring Boot 后端
├── frontend/            # Vue 3 前端
└── docs/
```

暂时不移动现有 Maven 工程，避免无意义的大规模路径变更。后续部署阶段可再整理为 `backend/frontend/deploy`。

## 前端技术栈

- Vue 3、TypeScript、Vite
- Vue Router、Pinia
- Element Plus、Lucide
- Axios、Fetch ReadableStream SSE
- Markdown-it、DOMPurify
- Vitest、Playwright

## 页面

- `/chat`：流式问答、历史会话、引用、工具调用、反馈、审计和动作生成。
- `/operations/overview`：readiness 和运行指标。
- `/operations/actions`：动作草稿列表。
- `/operations/quality`：反馈质量指标。
- `/operations/evaluation`：评测集和评测运行。
- `/operations/knowledge`：检索模式与真实搜索预览。
- `/operations/audit`：按 traceId 查询问答、工具和 RAG 审计。

## 本地通信

前端运行在 `127.0.0.1:5173`，Vite 把 `/api` 和 `/actuator` 代理到后端 `127.0.0.1:8080`。因此开发期不需要放开宽泛 CORS。

聊天流式接口仍为 `POST /api/agent/chat/stream`，前端使用 `fetch + ReadableStream` 解析 SSE，保留请求体和上下文请求头能力。

## 兼容策略

旧的 `chat.html` 和 `admin/actions.html` 暂时保留，作为迁移期间的回退入口。完成所有管理功能迁移、权限改造和生产 Nginx 部署后再删除。
