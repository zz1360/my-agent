# Logistics Agent Frontend

物流知识库 Agent 的独立 Vue 前端。

## 技术栈

- Vue 3 + TypeScript
- Vite
- Vue Router + Pinia
- Element Plus + Lucide Icons
- Axios + Fetch SSE
- Vitest + Playwright

## 本地开发

需要 Node.js 22.18+。项目根目录先启动 Spring Boot：

```bash
SPRING_PROFILES_ACTIVE=local \
  mvn -Dmaven.repo.local=/Users/zhangzhuang/Documents/develop/maven_repository spring-boot:run
```

再启动前端：

```bash
cd frontend
pnpm install
pnpm dev
```

访问：

- 对话台：`http://127.0.0.1:5173/chat`
- 管理台：`http://127.0.0.1:5173/operations/overview`

Vite 会把 `/api` 和 `/actuator` 代理到 `http://127.0.0.1:8080`。

## 验证

```bash
pnpm lint
pnpm test:unit --run
pnpm build
pnpm test:e2e --project=chromium
```

## 身份与权限

前端启动时调用 `/api/agent/security/context` 获取租户、用户、角色和权限。浏览器不保存企业 API Key。

本地开发由 Vite 代理读取 `DEV_AGENT_*` 环境变量并注入请求头；生产环境由 Nginx 容器或企业统一认证网关注入。多用户部署应由网关覆盖身份头，不能信任浏览器自行提交的身份信息。

## 容器运行

项目根目录准备好未提交的 `.env` 后运行：

```bash
docker compose -f docker-compose.prod.example.yml up -d --build
```

默认从 `http://localhost` 访问 Vue 前端，Nginx 将 `/api` 和 `/actuator` 同源代理到 Spring Boot。

旧的 Spring Boot 静态页面暂时保留，等 Vue 页面完整替换并稳定后再删除。
