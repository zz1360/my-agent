# v2.4 权限驱动的可操作管理台

本版把 Vue 管理台从只读查询升级为可执行运营工作台，覆盖身份权限、业务写操作、统一交互和生产部署。

## 身份与权限

`GET /api/agent/security/context` 新增 `permissions` 字段。权限由后端根据认证角色计算，当前包括：

- `CHAT_USE`
- `OPS_VIEW`
- `AUDIT_VIEW`
- `ACTION_MANAGE`
- `KNOWLEDGE_MANAGE`
- `QUALITY_MANAGE`
- `EVAL_MANAGE`

Vue 启动时读取安全上下文，Router Guard、菜单和操作按钮使用同一份权限。身份抽屉改成只读展示，不再允许浏览器手工修改租户、用户和角色。

开发环境由 Vite 服务端代理注入 `DEV_AGENT_*` 请求头。生产环境由 Nginx 或企业认证网关注入，服务 API Key 不进入 Vue 打包产物、Pinia 或 localStorage。

## 动作管理闭环

- 查看动作详情、证据和复核意见。
- 批准或驳回待复核动作。
- 高风险执行二次确认。
- 生成幂等键并执行动作。
- 查看执行记录、失败原因和重试次数。
- 对可重试失败发起人工重试。
- 查看动作写入业务表后的业务回链和 traceId。

## 知识运营闭环

- 新建和编辑知识文档。
- 管理文档类型、业务域、版本、ACL 和生效日期。
- 发布、停用和标记到期。
- 创建全量重建索引任务。
- 查看索引任务状态。
- 使用 keyword、hybrid、hybrid_reranker 做真实检索预览。

## 质量治理闭环

- 查看反馈质量指标。
- 主动运行质量告警评估。
- 从开放告警创建治理任务。
- 分派负责人。
- 将任务推进为 OPEN、PROCESSING、RESOLVED 或 REJECTED。

任务解决或驳回后，后端会同步解决对应质量告警，形成治理闭环。

## 评测与发布门禁

- 按评测集运行回归评测。
- 显式记录模型、知识库和提示词版本。
- 选择 baseline 与 candidate 查看提升和退化数量。
- 配置最低通过率和最大退化数并运行发布门禁。
- 展示 PASSED/BLOCKED 结果和阻断原因。

## 生产部署

`frontend/Dockerfile` 使用 Node + pnpm 构建 Vue，再由 Nginx 托管静态资源。Nginx 配置包含：

- SPA History 路由回退。
- `/api`、`/actuator` 同源反向代理。
- SSE 关闭代理缓冲并延长读取超时。
- 静态资源长期缓存，`index.html` 禁止缓存。
- CSP、X-Frame-Options、nosniff 等响应头。

`docker-compose.prod.example.yml` 同时启动前端和后端。固定 `AGENT_TENANT/USER/ROLES` 适合单一运营工作台或联调；多用户企业部署应放在 SSO/OIDC 网关后，由网关校验登录态并覆盖身份头。

## 自动验证

前端 CI 依次执行：

```text
pnpm lint
pnpm test:unit --run
pnpm build
pnpm exec playwright install --with-deps chromium
pnpm test:e2e --project=chromium
```

Playwright 覆盖聊天流式回答、管理台模块导航和无权限路由拦截。
