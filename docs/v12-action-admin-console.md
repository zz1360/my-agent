# v1.2 轻量管理页面

v1.2 在 v1.1 的管理后台接口基础上，补了一个不依赖前端构建工具的静态管理页面，让动作执行链路可以直接在浏览器里观察和操作。

访问地址：

```text
http://localhost:8080/admin/actions.html
```

## 核心变化

- 新增 `src/main/resources/static/admin/actions.html`，由 Spring Boot 静态资源能力直接托管。
- 页面聚合动作列表、执行日志、失败重试队列、执行指标和业务回链。
- 支持租户、用户、角色、客户、动作状态、执行状态、动作类型、目标系统筛选。
- 支持触发低风险动作自动执行。
- 支持对失败执行记录发起手动重试。
- 新增集成测试，验证 `/admin/actions.html` 可以通过 MockMvc 正常访问。

## 页面模块

| 模块 | 作用 | 主要接口 |
|---|---|---|
| 筛选栏 | 统一控制租户、角色、客户和状态范围 | 页面本地状态 |
| 执行指标 | 查看总执行数、成功率、失败率、可重试失败数 | `GET /api/agent/actions/executions/metrics` |
| 动作列表 | 查看诊断生成的动作草稿、审批状态和风险等级 | `GET /api/agent/actions` |
| 执行日志 | 查看动作执行历史、目标系统、状态和重试次数 | `GET /api/agent/actions/executions` |
| 失败重试队列 | 查看失败且可重试的执行记录 | `GET /api/agent/actions/executions/retry-queue` |
| 业务回链 | 查看动作最终写入的业务表记录 | `GET /api/agent/actions/{actionId}/business-link` |

## 操作能力

低风险自动执行：

```http
POST /api/agent/actions/automation/run
```

页面会把当前租户、用户、角色和客户筛选条件提交给后端，由后端扫描已审批且低风险的动作并执行。

失败执行重试：

```http
POST /api/agent/actions/executions/{executionId}/retry
```

页面只负责发起重试请求，真正的幂等判断、风险判断、最大重试次数控制仍在后端服务中完成。

## 设计取舍

这个页面是轻量管理台，不是完整运营后台：

- 采用原生 HTML、CSS、JavaScript，避免引入前端工程化复杂度。
- 所有权限、幂等、重试和业务写入规则仍由后端控制。
- 页面默认使用 `T001`、`OPS_MANAGER`、`C001`，便于开发阶段快速演示。
- 页面没有登录态、菜单系统和复杂分页，后续可替换为 Vue、React 或公司内部前端框架。

## 测试覆盖

新增测试：

- `actionAdminConsoleStaticPageLoads`

覆盖点：

- Spring Boot 可以正确暴露 `/admin/actions.html`。
- 页面包含管理台标题、执行指标、失败重试队列、业务回链等核心文案。
- 页面引用了执行指标接口路径，避免静态资源改动后核心调用入口丢失。

完整回归命令：

```bash
mvn -Dmaven.repo.local=/Users/zhangzhuang/Documents/develop/maven_repository test
```

## 下一步

v1.3 可以继续做“管理台交互闭环”：

- 给动作审批、驳回、强制执行增加页面操作。
- 增加服务端分页，避免执行日志变多后一次返回过大。
- 增加更细的错误提示，把权限不足、风险等级不允许、幂等命中分别展示。
- 接入真实登录态或网关透传身份，替换页面上的手动 `tenantId/userId/roles` 输入。
