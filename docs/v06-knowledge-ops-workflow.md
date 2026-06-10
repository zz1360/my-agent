# v0.6 知识库运营闭环

这一版把知识库从“可新增、可停用、可重建索引”继续推进为更接近真实运营后台的闭环：文档可以预览切片、按版本管理、走草稿到发布的状态流转，并且每次索引重建都会留下任务记录。

## 新增能力

- 文档版本组：`baseDocId` 用来表示同一篇制度或 SOP 的版本组，`docId` 表示某一个具体版本。
- 文档状态：复用 `status` 字段，支持 `DRAFT`、`ACTIVE`、`DISABLED`、`EXPIRED`。
- 切片预览：上传前先调用预览接口，看 chunk 数量、摘要和 metadata。
- 发布流转：发布某个版本时，同一个 `baseDocId` 下已有的 ACTIVE 版本会自动置为 `EXPIRED`。
- 有效期过滤：知识检索只召回 `ACTIVE` 且处在 `effectiveFrom/effectiveTo` 时间窗口内的文档。
- 索引任务：新增 `ai_knowledge_index_job`，记录触发类型、任务状态、chunk 数、PGVector 状态和失败原因。

## 新增接口

```text
POST /api/knowledge/documents/preview
POST /api/knowledge/documents/{docId}/publish
POST /api/knowledge/documents/{docId}/expire
GET  /api/knowledge/index-jobs
GET  /api/knowledge/index-jobs/{jobId}
```

旧接口保持可用：

```text
POST /api/knowledge/documents
GET  /api/knowledge/documents
GET  /api/knowledge/documents/{docId}
POST /api/knowledge/documents/{docId}/disable
POST /api/knowledge/reindex
GET  /api/knowledge/search
```

`GET /api/knowledge/documents` 新增 `baseDocId` 过滤参数，方便查看同一制度的不同版本。

## 典型流程

1. 先预览切片。
2. 保存为 `DRAFT`。
3. 运营确认内容后发布。
4. 发布后生成索引任务。
5. 查询索引任务状态。
6. 检索只命中最新 ACTIVE 版本。
7. 不再生效的版本可过期或停用。

示例请求：

```bash
curl -s http://localhost:8080/api/knowledge/documents/preview \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "T001",
    "userId": "u-ops-001",
    "roles": ["OPS_MANAGER"],
    "baseDocId": "sop-return-appointment",
    "docId": "sop-return-appointment-v1",
    "title": "逆向取件预约 SOP",
    "docType": "manual",
    "bizDomain": "reverse_logistics",
    "version": "v1.0",
    "status": "DRAFT",
    "aclRoles": ["CUSTOMER_SERVICE", "OPERATIONS", "OPS_MANAGER"],
    "content": "逆向取件预约需要先确认客户退货地址、可取件时间和包裹数量。"
  }'
```

发布：

```bash
curl -s -X POST \
  'http://localhost:8080/api/knowledge/documents/sop-return-appointment-v1/publish?tenantId=T001&userId=u-ops-001&roles=OPS_MANAGER'
```

查看索引任务：

```bash
curl -s \
  'http://localhost:8080/api/knowledge/index-jobs?tenantId=T001&userId=u-ops-001&roles=OPS_MANAGER&limit=20'
```

## 表结构变化

`ai_knowledge_document` 新增：

- `base_doc_id`：版本组 ID。
- `published_at`：发布时间。
- `indexed_at`：最近一次被索引任务覆盖的时间。

新增 `ai_knowledge_index_job`：

- `job_id`：任务 ID。
- `trigger_type`：触发来源，例如 `DOCUMENT_UPSERT`、`DOCUMENT_PUBLISH`、`DOCUMENT_DISABLE`、`DOCUMENT_EXPIRE`、`MANUAL_REINDEX`。
- `status`：`QUEUED`、`RUNNING`、`COMPLETED`、`FAILED`。
- `chunk_count`：本次索引覆盖的 chunk 数量。
- `vector_enabled`、`vector_ready`、`table_name`：PGVector 状态快照。
- `error_message`：失败原因。

## 验证

全量测试：

```bash
mvn -Dmaven.repo.local=/Users/zhangzhuang/Documents/develop/maven_repository test
```

当前结果：

```text
Tests run: 11, Failures: 0, Errors: 0, Skipped: 2
```
