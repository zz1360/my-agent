# v0.4 本地真实 Embedding、混合召回、Rerank 和 RAG 评测

这一版的目标是把知识检索从“关键词可用”推进到“本机语义向量可用，并且能被评测验证”。

## 这一版做了什么

- 引入 `TextEmbeddingService` 抽象，应用默认使用 Spring AI `TransformersEmbeddingModel` 加载本机 ONNX 模型。
- 将本地 hashing embedding 降级为测试和兜底 provider，不再作为默认生产配置。
- 选择 `BAAI/bge-small-zh-v1.5` 作为本机中文 embedding 模型，默认 512 维。
- `KnowledgeSearchService` 实现混合召回：PGVector 语义召回 + 关键词召回 + 轻量 rerank。
- `AgentEvalService` 增加 RAG 类型评测用例，验证检索是否命中指定 docId 和 chunkId。
- Flyway 增加 `V2__extend_agent_eval_for_rag.sql`，给评测表补充 RAG 指标字段。

## 本地 BGE 模型

模型来源是 [Xenova/bge-small-zh-v1.5](https://huggingface.co/Xenova/bge-small-zh-v1.5)，它提供了适合本地 Transformers.js/ONNX 使用的 `tokenizer.json` 和单文件 ONNX 权重。

模型文件不会提交到 Git。首次运行前执行：

```bash
scripts/download-bge-small-zh-v1.5.sh
```

脚本会下载：

```text
.local-models/bge-small-zh-v1.5/tokenizer.json
.local-models/bge-small-zh-v1.5/onnx/model_quantized.onnx
```

默认配置：

```yaml
agent:
  embedding:
    provider: transformers
    model-name: BAAI/bge-small-zh-v1.5
    model-uri: file:./.local-models/bge-small-zh-v1.5/onnx/model_quantized.onnx
    tokenizer-uri: file:./.local-models/bge-small-zh-v1.5/tokenizer.json
    model-output-name: last_hidden_state
    disable-caching: true
  vector-store:
    table-name: ai_knowledge_vector_chunk_v04
    dimension: 512
```

`disable-caching=true` 的意义是让 Spring AI 直接读取本机文件，避免默认模型地址触发远程缓存。DJL 第一次执行 mean pooling 时可能会缓存本机 native runtime，后续会复用本机缓存。

如果以后改成 `bge-m3`，核心变化是：

- 替换 `model-uri` 和 `tokenizer-uri`。
- 把 `agent.vector-store.dimension` 改成模型输出维度，`bge-m3` 常见是 1024 维。
- 使用新 PGVector 表名，或重建旧表，避免新旧维度冲突。

## 混合召回

单纯向量召回擅长语义相近，但有时会漏掉业务编号、SOP 名称、政策关键词。单纯关键词召回很稳定，但不理解同义表达。混合召回把两者合并：

1. 从 H2/MySQL 的 `ai_knowledge_chunk` 读取当前租户的 ACTIVE chunk，并按用户角色做 ACL 过滤。
2. 如果 PGVector ready，就用 query embedding 做语义召回。
3. 同时用领域关键词做关键词召回，例如“延误、赔付、冷链、温控、投诉、SLA”。
4. 按 `docId/chunkId` 合并候选，避免重复。
5. 对候选做 rerank，返回最高分的前 N 条。

当前 rerank 公式：

```text
finalScore = vectorScore * 0.56
           + keywordScore * 0.30
           + intentBoost * 0.08
           + termCoverage * 0.06
```

这不是大模型 reranker，而是一个轻量业务 reranker。它的好处是稳定、透明、容易写测试；下一版可以换成本地 cross-encoder reranker。

## RAG 评测

v0.4 给 `ai_eval_case` 增加了 `eval_type`。普通 Agent 回答仍是 `AGENT`，检索评测是 `RAG`。

默认新增两个 RAG 用例：

- `rag-delay-policy-hybrid`：验证“VIP 客户晚到补偿”能命中 `policy-delay-v3`。
- `rag-cold-chain-policy-hybrid`：验证“冷链超温处理”能命中 `policy-cold-chain-v2`。

运行方式不变：

```bash
curl -s -X POST 'http://localhost:8080/api/agent/evals/run?tenantId=T001'
```

RAG 结果会记录：

- `rag_hit_rate`：期望 doc/chunk 的命中率。
- `rag_top_doc_ids`：本次检索返回的 docId 列表。
- `rag_top_chunk_ids`：本次检索返回的 chunkId 列表。
- `rag_metrics_json`：query、topK、hitRate 和各候选分数。

## 验证命令

全量测试：

```bash
mvn -Dmaven.repo.local=/Users/zhangzhuang/Documents/develop/maven_repository test
```

真实本地 BGE embedding 单测：

```bash
mvn -Dmaven.repo.local=/Users/zhangzhuang/Documents/develop/maven_repository \
  -Dtest=TransformersEmbeddingModelTests \
  -Dlocal.bge.model-uri=file:/Users/zhangzhuang/Documents/develop/aiProject/superAgent/.local-models/bge-small-zh-v1.5/onnx/model_quantized.onnx \
  -Dlocal.bge.tokenizer-uri=file:/Users/zhangzhuang/Documents/develop/aiProject/superAgent/.local-models/bge-small-zh-v1.5/tokenizer.json \
  test
```

全量测试默认会跳过真实 BGE 单测，因为它加载模型较慢。集成测试用 hashing provider 保持快速稳定，应用运行配置则默认走本机 BGE。

## 后续方向

- 把轻量 rerank 替换或叠加本地 cross-encoder reranker，例如 `bge-reranker-base` 的 ONNX 版本。
- 给 PGVector 加 HNSW 索引，提升 chunk 数量上来后的查询速度。
- 增加离线评测集：覆盖更多物流场景、同义问法、错别字、业务编号和权限边界。
- 将 RAG 评测指标扩展为 Recall@K、MRR、NDCG，并输出趋势报表。
