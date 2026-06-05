# 云端 MySQL 业务库

当前业务库已从运行时 H2 切到云服务器 MySQL。表结构由 Flyway 管理，连接信息不写进代码，通过本机环境变量文件加载：

```bash
set -a
. /Users/zhangzhuang/Documents/script/ssh/spring-ai-demo-db-dev.env
set +a
```

该文件提供：

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

项目默认读取这些环境变量：

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:...}
    username: ${SPRING_DATASOURCE_USERNAME:...}
    password: ${SPRING_DATASOURCE_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

## 启动

```bash
set -a
. /Users/zhangzhuang/Documents/script/ssh/spring-ai-demo-db-dev.env
set +a
mvn -Dmaven.repo.local=/Users/zhangzhuang/Documents/develop/maven_repository spring-boot:run
```

首次启动时 Flyway 会创建或补齐这些表：

- `logistics_customer`
- `logistics_sla`
- `logistics_waybill`
- `logistics_tracking_event`
- `logistics_exception_event`
- `logistics_ticket`
- `ai_knowledge_document`
- `ai_knowledge_chunk`
- `ai_agent_trace`
- `ai_agent_tool_call`
- `ai_eval_case`
- `ai_eval_run`
- `ai_eval_case_result`

默认 `agent.demo.reset-on-start=false`，启动不会清空云端 MySQL 已有数据。如果需要重建演示数据，可以显式传入：

```bash
mvn -Dmaven.repo.local=/Users/zhangzhuang/Documents/develop/maven_repository spring-boot:run \
  -Dspring-boot.run.arguments="--agent.demo.reset-on-start=true"
```

## 测试

单元测试不依赖云服务器，`src/test/resources/application.yml` 会把 datasource 切到 H2 MySQL mode：

```bash
mvn -Dmaven.repo.local=/Users/zhangzhuang/Documents/develop/maven_repository test
```
