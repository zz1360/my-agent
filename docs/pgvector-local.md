# 本机 PGVector 方案

第一版真实向量库选择 PGVector。

原因：

- Mac 本机安装简单，可以用 Docker 一条命令启动。
- PostgreSQL + pgvector 适合从 MVP 过渡到生产。
- Spring AI 有 PGVector VectorStore 适配，后续可以把当前 `KnowledgeSearchService` 替换为真实向量召回。
- 向量库和业务库解耦；当前业务库默认使用云端 MySQL，测试环境仍可用 H2。

## Homebrew 启动方式

本机如果没有 Docker，推荐使用 Homebrew：

```bash
brew install postgresql@17 pgvector
brew services start postgresql@17
```

如果 5432 已被旧版本 PostgreSQL 占用，先停旧服务，例如：

```bash
brew services stop postgresql@14
brew services start postgresql@17
```

创建应用数据库和用户：

```bash
export PGVECTOR_PASSWORD='your_local_pgvector_password'

/opt/homebrew/opt/postgresql@17/bin/psql -h localhost -p 5432 -d postgres \
  -c "CREATE ROLE logistics_agent LOGIN PASSWORD '${PGVECTOR_PASSWORD}';"

/opt/homebrew/opt/postgresql@17/bin/createdb -h localhost -p 5432 \
  -O logistics_agent logistics_agent

/opt/homebrew/opt/postgresql@17/bin/psql -h localhost -p 5432 -d logistics_agent \
  -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

启动应用后确认状态：

```bash
curl -s http://localhost:8080/api/demo/vector-store/status
```

期望结果：

```json
{"provider":"pgvector","enabled":true,"ready":true,"chunks":10,"table":"ai_knowledge_vector_chunk"}
```

直接检查向量表：

```bash
/opt/homebrew/opt/postgresql@17/bin/psql -h localhost -p 5432 -d logistics_agent \
  -Atc "select count(*), min(vector_dims(embedding)), max(vector_dims(embedding)) from ai_knowledge_vector_chunk;"
```

期望结果类似：

```text
10|384|384
```

## Docker 启动方式

如果本机有 Docker，也可以使用项目里的 compose 文件：

```bash
export PGVECTOR_PASSWORD='your_local_pgvector_password'
docker compose -f docker-compose.pgvector.yml up -d
```

连接信息：

```text
url: jdbc:postgresql://localhost:5432/logistics_agent
user: logistics_agent
password: 通过 PGVECTOR_PASSWORD 环境变量提供
```

后续接入点：

- 当前已实现：启动时将业务库中的 `ai_knowledge_chunk` 同步到 PGVector 表 `ai_knowledge_vector_chunk`。
- 当前已实现：`KnowledgeSearchService.search(...)` 优先使用 PGVector 的 `<=>` 向量相似度检索。
- 后续可增强：将本地 hashing embedding 替换成真实 embedding 模型。
- 后续可增强：增加 HNSW / IVFFlat 索引和文档增量入库接口。
