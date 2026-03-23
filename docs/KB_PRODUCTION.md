# 知识库（KB）生产级注意事项

## 已落实的工程实践

| 项          | 说明                                                                                                                                                |
|------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| **事务边界**   | `ingestTextDocument`：短事务 `insertPending` → 事务外 `embed` → **外置向量库 upsert**（Milvus 含 flush）→ 短事务 `markReady`，避免长事务与外部 IO 耦合。                        |
| **体量上限**   | `agentlego.kb.ingest.max-document-chars`、`max-chunks` 限制正文与分片数；防止拖垮 embedding API 与向量库。                                                           |
| **校验**     | 集合名/文档标题、`vectorStoreConfig`（Milvus / Qdrant 连接与 collection 名等）、embedding 维度与 `embedding_dims` 一致（`ModelEmbeddingDimensions.fitToCollectionDim`）。 |
| **错误状态**   | 失败路径 `markFailed` 落库，避免长期 `PENDING`。                                                                                                              |
| **删除一致性**  | 删文档：先按 `document_id` 删向量，再删 PG。删集合：从智能体 `knowledge_base_policy` 剥离 → 删 PG → **drop** 物理 collection（若向量库失败，PG 已删，需运维按需清理孤儿 collection）。            |
| **相似度计算**  | **在 Milvus / Qdrant 内完成**；Java 侧对 Milvus L2、Qdrant Euclid 等做分数归一到「越大越相似」并与 `scoreThreshold` 比较。                                                   |
| **混合检索**   | **已移除** PostgreSQL 全文 + RRF；`knowledge_base_policy.fullTextEnabled` 与 `agentlego.kb.retrieve.fulltext-enabled` **不再参与** RAG（保留键仅为兼容旧 JSON）。       |
| **DTO 映射** | `KbDtoMapper`（MapStruct）维护集合/文档 DTO。                                                                                                              |

## 本地示例数据

若仓库内提供 `scripts/seed-kb-samples.mjs` 等种子脚本，需为集合配置可用的 **Milvus 或 Qdrant** 与 **embedding 模型**。

## 建议的后续增强（按优先级）

1. **异步入库**：大文档改为队列 / `@Async`，HTTP 返回 `docId` + `PENDING`。
2. **幂等与去重**：标题 + 内容哈希或 `Idempotency-Key`。
3. **速率限制**：对 `/kb/.../documents` 限流。
4. **可观测性**：embed 耗时、Milvus 延迟、`markFailed` 原因（Micrometer + 结构化日志）。
5. **多租户**：`lego_kb_*` 增加 `tenant_id` 并贯穿查询与 Milvus 命名空间策略。

## MapStruct 与全项目

KB 模块使用 MapStruct 作为样板；其他上下文可按需逐步引入。
