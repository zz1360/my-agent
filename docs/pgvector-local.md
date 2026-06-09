# 本机 PGVector 方案

本机真实向量库选择 PGVector。

原因：

- Mac 本机安装简单，可以用 Docker 一条命令启动。
- PostgreSQL + pgvector 适合从 MVP 过渡到生产。
- 当前系统已经把知识 chunk 同步到 PGVector，并使用本地 BGE embedding 做向量召回。
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
{"provider":"pgvector","enabled":true,"ready":true,"chunks":10,"table":"ai_knowledge_vector_chunk_v04"}
```

直接检查向量表：

```bash
/opt/homebrew/opt/postgresql@17/bin/psql -h localhost -p 5432 -d logistics_agent \
  -Atc "select count(*), min(vector_dims(embedding)), max(vector_dims(embedding)) from ai_knowledge_vector_chunk_v04;"
```

期望结果类似：

```text
10|512|512
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

v0.4 接入点：

- 当前已实现：启动时将业务库中的 `ai_knowledge_chunk` 同步到 PGVector 表 `ai_knowledge_vector_chunk_v04`。
- 当前已实现：默认使用本机 `BAAI/bge-small-zh-v1.5` ONNX 模型生成 512 维语义向量。
- 当前已实现：`KnowledgeSearchService.search(...)` 会合并 PGVector 向量召回和关键词召回，再做轻量 rerank。
- 后续可增强：增加 HNSW / IVFFlat 索引和文档增量入库接口。

如果本机曾经创建过旧版 `ai_knowledge_vector_chunk` 384 维表，可以保留它不动。v0.4 默认使用新表名，避免 384 维旧表和 512 维 BGE 向量发生维度冲突。
