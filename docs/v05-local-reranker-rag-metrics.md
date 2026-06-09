# v0.5 本地 Reranker 和 RAG 评测指标升级

这一版的目标是把 v0.4 的“透明规则 rerank”升级为“本地 cross-encoder reranker 可用”，同时把 RAG 评测从单一命中率扩展为更完整的排序指标。

## 本地 Reranker

默认模型来源是 [Xenova/bge-reranker-base](https://huggingface.co/Xenova/bge-reranker-base)。它提供 `tokenizer.json` 和量化 ONNX 权重，适合本机 Java 进程内用 ONNX Runtime 执行。

首次运行前下载模型：

```bash
scripts/download-bge-reranker-base.sh
```

模型文件会放到：

```text
.local-models/bge-reranker-base/tokenizer.json
.local-models/bge-reranker-base/onnx/model_quantized.onnx
```

该目录已被 `.gitignore` 忽略，不会提交到远程仓库。

默认配置：

```yaml
agent:
  reranker:
    provider: onnx
    fallback-to-lightweight: true
    model-name: Xenova/bge-reranker-base
    model-uri: file:./.local-models/bge-reranker-base/onnx/model_quantized.onnx
    tokenizer-uri: file:./.local-models/bge-reranker-base/tokenizer.json
    model-output-name: logits
    max-candidates: 24
    model-weight: 0.72
    rule-weight: 0.28
```

`fallback-to-lightweight=true` 的意义是：本机模型文件还没下载时，应用仍可启动并回退到规则 rerank。测试环境显式使用：

```yaml
agent:
  reranker:
    provider: lightweight
```

## 检索链路变化

v0.5 的检索链路是：

```text
用户问题
  -> PGVector 语义召回
  -> 关键词召回
  -> 规则分候选
  -> 本地 ONNX reranker 精排
  -> 返回 topK 引用
```

本地 reranker 是 cross-encoder：它不是分别给 query 和 chunk 算向量，而是把“query + chunk”作为一对输入，直接输出相关性 logits。它通常比单纯向量相似度更适合做最后一轮精排。

当前最终分数：

```text
finalScore = rerankerScore * 0.72 + ruleScore * 0.28
```

其中 `ruleScore` 来自 v0.4 的混合召回规则分，`rerankerScore` 是 ONNX logits 经过 sigmoid 后的值。

## RAG 评测指标

v0.4 只有 `rag_hit_rate`。v0.5 保留它作为兼容字段，同时新增：

- `rag_recall_at_k`：期望 doc/chunk 的覆盖率。
- `rag_precision_at_k`：topK 结果里相关结果占比。
- `rag_mrr`：第一个相关结果的倒数排名。
- `rag_ndcg`：相关结果排得越靠前越高。
- `rag_expected_total`：期望命中项数量。
- `rag_hit_count`：实际命中项数量。

接口返回 `ragMetricsJson` 会包含分项指标和候选分数：

```json
{
  "recallAtK": 1.0,
  "precisionAtK": 0.2,
  "mrr": 1.0,
  "ndcg": 1.0,
  "scores": [
    {
      "docId": "policy-cold-chain-v2",
      "chunkId": "policy-cold-chain-v2-chunk-001",
      "score": 0.93,
      "ruleScore": 0.42,
      "rerankerScore": 0.98,
      "rerankerProvider": "onnx:Xenova/bge-reranker-base"
    }
  ]
}
```

这样排查问题会更细：

- Recall 低：检索没覆盖到该找的资料。
- Precision 低：topK 里噪声多。
- MRR/NDCG 低：找到了，但排序靠后，reranker 需要调优。

## 验证命令

全量测试：

```bash
mvn -Dmaven.repo.local=/Users/zhangzhuang/Documents/develop/maven_repository test
```

本地 reranker 模型验证：

```bash
mvn -Dmaven.repo.local=/Users/zhangzhuang/Documents/develop/maven_repository \
  -Dtest=LocalOnnxKnowledgeRerankerTests \
  -Dlocal.reranker.model-uri=file:./.local-models/bge-reranker-base/onnx/model_quantized.onnx \
  -Dlocal.reranker.tokenizer-uri=file:./.local-models/bge-reranker-base/tokenizer.json \
  test
```

如果只想快速验证 Java ONNX reranker 链路，也可以下载更小的 cross-encoder smoke 模型：

```bash
scripts/download-ms-marco-minilm-reranker.sh

mvn -Dmaven.repo.local=/Users/zhangzhuang/Documents/develop/maven_repository \
  -Dtest=LocalOnnxKnowledgeRerankerTests \
  -Dlocal.reranker.model-name=Xenova/ms-marco-MiniLM-L-6-v2 \
  -Dlocal.reranker.model-uri=file:./.local-models/ms-marco-MiniLM-L-6-v2/onnx/model_quantized.onnx \
  -Dlocal.reranker.tokenizer-uri=file:./.local-models/ms-marco-MiniLM-L-6-v2/tokenizer.json \
  test
```
