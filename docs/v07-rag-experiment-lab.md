# v0.7 RAG 检索质量实验台

这一版新增 RAG 检索质量实验台，用来把同一个问题放到不同检索策略下对比，观察 Recall@K、Precision@K、MRR、NDCG 和检索耗时。

## 为什么需要实验台

RAG 效果不好时，问题通常不一定在大模型，也可能在检索阶段：

- chunk 切得不好。
- 关键词召回漏了。
- 向量召回排序靠后。
- reranker 权重不合适。
- topK 太小或太大。

实验台的价值是把“感觉更准”变成可记录、可比较的指标。

## 支持的检索模式

| 模式 | 含义 |
|---|---|
| `KEYWORD_ONLY` | 只用关键词召回和规则分排序 |
| `VECTOR_ONLY` | 只用 PGVector 语义召回，PGVector 不可用时结果为空 |
| `HYBRID_RULE` | 向量 + 关键词召回，但不启用 reranker |
| `HYBRID_RERANKER` | 向量 + 关键词 + 当前配置的 reranker |

默认普通知识搜索仍走原来的完整链路。实验台通过 `KnowledgeSearchOptions` 调用同一个检索服务的不同策略分支。

## 新增接口

```text
GET  /api/rag/experiments
POST /api/rag/experiments
GET  /api/rag/experiments/{experimentId}
POST /api/rag/experiments/{experimentId}/run
GET  /api/rag/experiments/{experimentId}/runs
```

## 创建实验

```bash
curl -s http://localhost:8080/api/rag/experiments \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "T001",
    "userId": "u-rag-001",
    "roles": ["CUSTOMER_SERVICE"],
    "experimentId": "raglab-cold-chain-demo",
    "name": "冷链超温实验",
    "query": "冷链运输温度超过 10C 后客服应该怎么处理？",
    "expectedDocIds": ["policy-cold-chain-v2"],
    "expectedChunkIds": ["policy-cold-chain-v2-chunk-001"],
    "topK": 5,
    "modes": ["KEYWORD_ONLY", "HYBRID_RULE", "HYBRID_RERANKER"]
  }'
```

## 运行实验

```bash
curl -s -X POST \
  'http://localhost:8080/api/rag/experiments/raglab-cold-chain-demo/run?tenantId=T001&roles=CUSTOMER_SERVICE'
```

也可以只运行部分模式：

```bash
curl -s -X POST \
  'http://localhost:8080/api/rag/experiments/raglab-cold-chain-demo/run?tenantId=T001&roles=CUSTOMER_SERVICE&modes=KEYWORD_ONLY&modes=HYBRID_RERANKER'
```

## 指标说明

- `recallAtK`：期望文档或 chunk 有多少被 topK 召回。
- `precisionAtK`：topK 结果里有多少比例是期望结果。
- `mrr`：第一个相关结果的倒数排名，正确结果越靠前越高。
- `ndcg`：相关结果排序质量，越靠前越高。
- `latencyMs`：本次检索耗时。
- `metricsJson`：包含 query、mode、topK、分数明细和每个候选的 provider。

## 表结构

新增：

```text
ai_rag_experiment
ai_rag_experiment_run
```

`ai_rag_experiment` 保存实验定义，包括 query、期望 doc/chunk、topK 和模式列表。

`ai_rag_experiment_run` 保存每次运行结果。一个实验运行多个模式时，会为每个模式写一条结果，便于按模式对比。

## 验证

全量测试：

```bash
mvn -Dmaven.repo.local=/Users/zhangzhuang/Documents/develop/maven_repository test
```

当前结果：

```text
Tests run: 12, Failures: 0, Errors: 0, Skipped: 2
```
