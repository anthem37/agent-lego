# 知识库设计说明（Milvus / Qdrant）

> **全新部署**仅执行 Flyway **`V1__baseline.sql`**：PostgreSQL **不**使用 pgvector，无 `lego_kb_chunks` 表；向量仅存 *
*外置向量库**（`MILVUS` 或 `QDRANT`）。

## 向量存在哪？

- **PostgreSQL**：集合与文档元数据（标题、正文 Markdown、富文本、工具绑定、`status` 等）。
- **Milvus**：每个「知识集合」对应用户配置的**一个物理 collection**（`vector_store_config.collectionName`），字段含
  `chunk_id`、`document_id`、`chunk_index`、`chunk_text`、`embedding`（维度 = 集合的 `embedding_dims`）。
- **Qdrant**：每个集合对应**一个物理 collection**；点 ID 为 Snowflake `chunk_id` 的整数值；payload 存 `chunk_id`、
  `document_id`、`chunk_index`、`chunk_text`；REST 接入（`url` 或 `host`+`port`+`secure`、可选 `apiKey`）。

相似度检索在 **各向量库内**完成（`MilvusKnowledgeStore` / `QdrantVectorStore`），经 `DelegatingKbVectorStore` 按集合路由；
`KbVectorRetrieveEngine` 负责跨集合合并与 `RetrieveConfig` 阈值过滤。

## 目标

- **可维护**：集合 → 文档 → 分片（仅逻辑分片；向量在外置库）。
- **可配置**：`vector_store_kind`（**`MILVUS` | `QDRANT`**）与 `vector_store_config` 按集合配置。
- **运行时对齐**：`KbVectorKnowledge` 实现 AgentScope `Knowledge`，与 `KbRetrievedChunkRenderer`、会话工具出参协同。

## 数据模型（当前）

| 表                     | 说明                                                                                                                     |
|-----------------------|------------------------------------------------------------------------------------------------------------------------|
| `lego_kb_collections` | `embedding_model_id`、`embedding_dims`、`vector_store_kind`、`vector_store_config`（jsonb）、`chunk_strategy`、`chunk_params` |
| `lego_kb_documents`   | 逻辑文档；`body` / `body_rich`；`status`：`PENDING` / `READY` / `FAILED`                                                      |

## 应用层（后端）

- **`KbApplicationService`**：集合/文档 CRUD、控制台召回预览、入库分片与向量写入、删除集合时清理智能体策略并
  `dropPhysicalCollection`。
- **`KbDocumentValidator`**（`kb.application.validation`）：文档校验（工具绑定、正文 `{{tool:}}`、`tool_field` 等），入库与控制台复用。
- **`tool_field` 与工具分类**：仅 **「查询」类工具**（`lego_tools.tool_category = QUERY`）允许在知识正文嵌入出参字段（
  `{{tool_field:…}}` 及 bindings 中 `tool_field:` 占位）；操作类（ACTION）会校验失败。

## 运维要求

- **PostgreSQL**：标准镜像即可（仅需 `pg_trgm`）；库结构见 `db/migration/V1__baseline.sql`。
- **Milvus / Qdrant**：每个集合至少可达用户填写的连接；删除平台集合时会 **尝试删除对应物理 collection**（在 PG 删除成功后由
  `KbApplicationService.deleteCollection` 调用 `KbVectorStore.dropPhysicalCollection`）。

## 智能体策略 `knowledge_base_policy`（jsonb）

```json
{
  "collectionIds": [
    "集合ID1",
    "集合ID2"
  ],
  "topK": 5,
  "scoreThreshold": 0.25,
  "embeddingModelId": ""
}
```

- `fullTextEnabled`：**已废弃**，保留于旧数据时不影响检索（仅外置向量库）。

## 文本分片策略（集合级）

与此前一致：**固定窗口**、**段落**、**Markdown 标题分节**。见 `GET /kb/meta/chunk-strategies`。

- 创建集合：`POST /kb/collections` 需 **`vectorStoreConfig`**（及 `embeddingModelId`、分片相关字段等）。
- 创建后分片策略不可变（无 PATCH 集合）。

## API（REST）要点

- `POST /kb/collections`：写入 `vector_store_kind` / `vector_store_config`、embedding 与分片配置。
- `POST/PUT .../documents`：分片 → `ModelEmbeddingClient.embed` → **向量库 upsert** → `markReady`。
- `DELETE .../documents/...`：先删该 `document_id` 的向量，再删 PG 行。
- `DELETE /kb/collections/{id}`：从智能体策略剥离 → 删 PG 集合（级联文档）→ **drop 物理 collection**。

## RAG 运行时（`com.agentlego.backend.kb`）

| 组件                               | 职责                                                |
|----------------------------------|---------------------------------------------------|
| `vector.DelegatingKbVectorStore` | 按集合 `vector_store_kind` 路由                        |
| `milvus.MilvusKnowledgeStore`    | Milvus：建 collection/索引、insert、flush、delete、search |
| `qdrant.QdrantVectorStore`       | Qdrant：REST 建集合、upsert、filter delete、search、删集合   |
| `rag.KbVectorRetrieveEngine`     | 多集合 query embedding + 逐库 search + 合并排序            |
| `runtime.KbVectorKnowledge`      | AgentScope `Knowledge.retrieve`                   |
| `rag.KbRetrievedChunkRenderer`   | 片段注入模型前：`tool_field`、`{{tool:}}` 等                |
| `rag.KbRagKnowledgeFactory`      | 按策略装配 `Knowledge` + topK/threshold                |

## 限制与后续

- 单向量字段维度上限见 `ModelEmbeddingDimensions.VECTOR_STORE_MAX_DIM`（默认 8192，可按部署调优）。
- 多集合 RAG 要求 **相同 `embedding_model_id`** 且 **`embedding_dims` 一致**（与 query 向量维度对齐）。
