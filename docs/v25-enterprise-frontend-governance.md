# v2.5 企业前端治理

## 版本目标

本版本将前端从“可用的运营台”升级为更适合多用户企业运行的 Web 应用，落地认证会话、服务端状态治理、构建性能预算和浏览器质量门禁。

## 1. OIDC/BFF 身份体系

- 默认 `api-key` 模式继续服务本地开发与自动化调用。
- `oidc-bff` 模式由 Spring Security OAuth2 Client 完成授权码登录。
- OIDC Token 只保存在服务端 Session，浏览器只持有 `HttpOnly + SameSite=Lax + Secure` Cookie。
- 写请求使用 `XSRF-TOKEN` Cookie 与 `X-XSRF-TOKEN` 请求头防护 CSRF。
- OIDC Claim 映射为 `tenantId/userId/roles`，业务层在企业认证模式下忽略浏览器提交的身份字段。
- 管理写接口使用 `@PreAuthorize` 做方法级权限检查。

启用方式：

```bash
export SPRING_PROFILES_ACTIVE=prod,oidc
export AGENT_AUTH_MODE=oidc-bff
export AGENT_OIDC_ISSUER_URI=https://idp.example.com/realms/logistics
export AGENT_OIDC_CLIENT_ID=logistics-agent
export AGENT_OIDC_CLIENT_SECRET=replace-me
```

身份提供方需要在 ID Token 或 UserInfo 中提供 `tenant_id`、`preferred_username` 和 `roles`；Claim 名称可通过 `AGENT_OIDC_*_CLAIM` 调整。默认回调地址是 `/login/oauth2/code/corporate`。

## 2. 服务端状态与分页

动作、知识文档、质量告警、治理任务、评测运行和发布门禁新增 `/page` 接口。后端执行 `COUNT + LIMIT + OFFSET`，统一返回：

```json
{"items":[],"page":1,"size":20,"total":0,"totalPages":0}
```

前端使用 TanStack Vue Query 管理缓存、加载状态、失败重试和变更后的刷新。`ServerPagination`、`StatusTag` 与确认操作封装减少了管理页重复代码。

## 3. 性能预算

- Element Plus 改成组件和样式按需导入。
- Markdown 与 Vue Query 拆成独立 chunk。
- 移除未使用的 ECharts 依赖。
- `pnpm build` 自动运行 Bundle Budget：首屏 JS gzip 不超过 250 KiB，单个异步路由入口不超过 80 KiB。

本版本实测首屏为 `72.4 KiB gzip`，最大路由入口为 `33.8 KiB gzip`。

## 4. 可观测与质量门禁

浏览器为 API 请求生成 `X-Trace-Id`；错误提示会显示后端返回的 traceId。前端采集窗口错误、未处理 Promise、Vue 错误、API 失败/耗时、路由耗时、LCP 和 CLS，通过 `/api/ops/frontend-events` 写入结构化日志与 Micrometer 指标。

Playwright 覆盖：

- 对话流式输出与权限路由。
- 动作复核、知识文档新增、质量评估、评测集运行。
- Pixel 5 移动导航与布局快照。
- axe 严重/关键级无障碍问题扫描。

CI 同时运行 `chromium` 和 `mobile-chrome`，构建体积超限或布局快照变化都会阻断合并。
