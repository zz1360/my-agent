# v2.1 企业运维基线：Profile、可观测性与发布门禁

v2.1 的目标不是新增一个业务页面，而是把物流 Agent 从“能跑”推进到“更像企业应用地运行”。本版本补齐了环境 Profile、启动配置校验、运维健康检查、指标摘要、CI 门禁和敏感配置治理。

## 环境 Profile

系统拆分为三个常用环境：

- `local`：本机开发环境，默认使用 H2 文件库、hashing embedding、lightweight reranker，不要求 DeepSeek 和 PGVector 必须可用。
- `dev`：联调环境，业务库使用外部 MySQL，DeepSeek 和 PGVector 默认启用，参数从环境变量注入。
- `prod`：生产环境，开启部署配置 fail-fast，要求 API Key、DeepSeek key 文件、PGVector 连接信息显式配置。

启动示例：

```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

生产环境不建议在命令行明文写密码，应由部署平台、Kubernetes Secret、GitHub Actions Secret 或服务器侧 `.env` 注入。

## 启动配置校验

`DeploymentConfigValidator` 会在应用启动时检查关键配置：

- `prod` 环境必须启用入口 API Key。
- DeepSeek 启用时必须配置 key 文件路径。
- 默认检索模式为 `VECTOR_ONLY` 时，必须启用 PGVector。
- 生产环境启用 PGVector 时必须配置连接密码。
- PGVector 维度必须为正数。

`agent.deployment.fail-fast=true` 时，发现问题会直接终止启动；本地环境默认只打印告警，方便开发。

## 运维健康检查

系统新增两个运维 API：

- `GET /api/ops/readiness`：聚合业务库、Flyway、PGVector、DeepSeek、默认检索策略状态。
- `GET /api/ops/metrics/summary`：返回问答量、平均延迟、RAG recall、工具成功率、发布门禁通过和阻断数量。

同时引入 Spring Boot Actuator：

- `GET /actuator/health`
- `GET /actuator/info`
- `GET /actuator/metrics`

自定义健康项包括：

- `deepSeek`
- `pgVector`
- `retrieval`
- `flywayVersion`

## 指标设计

`OpsMetricsService` 暴露了可被 Actuator 采集的 Micrometer 指标：

- `logistics.agent.questions.total`
- `logistics.agent.latency.avg.ms`
- `logistics.agent.rag.recall.latest`
- `logistics.agent.tool.success.rate`
- `logistics.agent.release_gate.passed.total`
- `logistics.agent.release_gate.blocked.total`

这些指标适合后续接 Prometheus、Grafana 或云厂商 APM。

## GitHub Actions CI 发布门禁

`.github/workflows/ci.yml` 做三类检查：

- 禁止明显敏感文件被提交，例如 `.env`、`application-secret.yml`、含 key/password/secret 的文件名。
- 扫描常见真实密钥形态，例如 OpenAI/DeepSeek 风格长 key、AWS key、私钥块。
- 执行 `mvn -B test` 和 `mvn -B -DskipTests package`。

这不是完整 DevSecOps，但已经形成最小发布门禁：代码能编译、测试能过、明显密钥不会被带进远程仓库。

## 配置治理

项目只提交 `.env.example`，不提交真实 `.env`。真实敏感值应放在：

- 本机 shell 环境变量。
- 服务器侧受控 env 文件。
- CI/CD Secret。
- 容器编排平台 Secret。

`.env.example` 里的密码和 API Key 都是占位符，不代表真实可用凭据。

## 对 Agent 项目的价值

Agent 应用比普通 CRUD 更需要运维兜底，因为它同时依赖模型、知识库、业务库、检索策略和工具调用链路。v2.1 的价值在于：启动前能发现配置问题，运行时能看到关键依赖是否可用，发布前能用测试和敏感信息检查挡住低级事故。
