# 知识库 v3 设计说明

## 向量相似度由谁算？

**由 PostgreSQL pgvector 在 SQL 里算，不是业务 Java 里对全表循环算余弦。**

- 列类型：`vector(3072)`；索引：`USING hnsw (embedding_vec vector_cosine_ops)`（余弦距离）。
- 检索：`ORDER BY embedding_vec <=> CAST(:query AS vector)`，其中 `<=>` 在 `vector_cosine_ops` 下为**余弦距离**
  （0=完全相同，2=相反）。
- 若需要「相似度」分数展示，可在 SQL 写 `1 - (embedding_vec <=> query)`（与距离单调对应），见 `KbChunkMapper.xml`。

## 目标

- **可维护**：与「记忆」解耦；知识以**集合 → 文档 → 分片**管理，生命周期清晰。
- **向量检索**：分片向量存 **PostgreSQL pgvector**（`vector(3072)` + **HNSW 余弦索引**），检索用 SQL `<=>` 排序；短于 3072 维的
  embedding **零填充** 后入库（余弦与未填充等价）。
- **与 AgentScope 对齐**：运行时实现 `io.agentscope.core.rag.Knowledge`，在 `AgentRuntime` 中走 `RAGMode.GENERIC`。

## 数据模型

| 表                | 说明                                                              |
|------------------|-----------------------------------------------------------------|
| `kb_collections` | 知识集合；绑定 `embedding_model_id`；`embedding_dims` 记录上游输出维度（≤3072）。  |
| `kb_documents`   | 逻辑文档；`body` 为全文；`status`：`PENDING` / `READY` / `FAILED`。        |
| `kb_chunks`      | 检索单元；`content` + **`embedding_vec vector(3072)`**（无 JSONB 向量列）。 |

## 运维要求

- PostgreSQL 需启用 **`vector` 扩展**（基线迁移 `V1__baseline.sql` 内 `CREATE EXTENSION IF NOT EXISTS vector`）。
- 若托管库禁止自建扩展，需运维预先打开 pgvector 或使用带 pgvector 的镜像。
- 本地可试用仓库根目录 **`docker-compose.postgres-pgvector.yml`**（`pgvector/pgvector:pg16`）。

## 索引维护

- 向量索引名为 **`ix_kb_chunks_embedding_vec_hnsw_partial`**（部分 HNSW）。大批量导入后若检索延迟异常，可在低峰期对
  `kb_chunks` 执行
  **`REINDEX INDEX ix_kb_chunks_embedding_vec_hnsw_partial`**（注意云厂商对 REINDEX 的事务/锁限制）。

## 智能体策略 `knowledge_base_policy`（jsonb）

```json
{
  "collectionIds": ["集合ID1", "集合ID2"],
  "topK": 5,
  "scoreThreshold": 0.25,
  "embeddingModelId": ""
}
```

## API（REST）

- `POST /kb/collections`：创建集合（写入 `embedding_dims`）。
- `GET /kb/collections`：列表。
- `GET /kb/collections/{id}`：详情。
- `POST /kb/collections/{id}/documents`：写入文档（分片 + 向量化 + 写入 `embedding_vec`）。
- `DELETE /kb/collections/{collectionId}/documents/{documentId}`：删除文档（级联删除分片）。
- `DELETE /kb/collections/{id}`：删除集合（级联删除文档与分片）；并**自动**从所有智能体
  `knowledge_base_policy.collectionIds` 中移除该 id，若移除后无剩余集合则策略整段置为 `{}`。响应 `data` 为
  `{ "agentsPolicyUpdated": N }`，表示写回了策略的智能体数量。

## 限制与后续

- 上游维度 **大于 3072** 的模型配置会被拒绝创建集合。
- 候选检索条数见 `KbRagKnowledgeFactory.DEFAULT_CANDIDATE_LIMIT`；超大库可调高或引入专用向量库。
- `Knowledge.addDocuments` 仍为空实现（入库走平台 API）。
