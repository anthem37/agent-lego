# 知识库（KB）生产级注意事项

## 已落实的工程实践

| 项          | 说明                                                                                                                                                                  |
|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **事务边界**   | `ingestTextDocument` 不再用单一大 `@Transactional` 包住 embedding 调用：先短事务 `insertPending`，在事务外调用 `ModelEmbeddingClient.embed`，再单事务批量 `insert` 分片并 `markReady`，降低锁持有时间与连接占用。 |
| **体量上限**   | `agentlego.kb.ingest.max-document-chars`（默认 512Ki 字符量级）、`max-chunks`（默认 2000）防止超大文档拖垮 embedding API 与 DB。DTO 层 `@Size` 与业务层二次校验对齐。                                  |
| **校验**     | 集合名 / 文档标题长度、body 非空、embedding 维度与 pgvector 列宽一致性校验。                                                                                                                |
| **错误状态**   | 失败路径 `markFailed` 落库，避免长期 `PENDING`。                                                                                                                                |
| **删除一致性**  | 删集合前从智能体 `knowledge_base_policy` 剥离 `collectionIds`（与 `AgentRepository` 协同）。                                                                                        |
| **向量安全**   | `KbPgVectorLiteral` 仅格式化平台侧 `float[]`，查询向量由参数绑定字面量生成，不把用户原文拼进 SQL。                                                                                                  |
| **相似度计算**  | **在 PostgreSQL 内由 pgvector 完成**：`embedding_vec <=> query`（余弦距离）+ HNSW；Java 只做阈值过滤与组装 `Document`，见 `KbChunkMapper.xml` / `docs/KB_REDESIGN.md`。                      |
| **DTO 映射** | KB 读模型 → API DTO 使用 **MapStruct**（`KbDtoMapper`），减少手写映射错误。                                                                                                          |

## 建议的后续增强（按优先级）

1. **异步入库**：大文档改为消息队列 / `@Async` 任务，HTTP 立即返回 `docId` + `PENDING`，客户端轮询或 WebSocket 推送状态。
2. **幂等与去重**：对同一集合下「标题 + 内容哈希」去重或显式 `Idempotency-Key` 头。
3. **速率限制**：按租户或 IP 对 `/kb/.../documents` 限流（网关或 Bucket4j）。
4. **可观测性**：对 `embed` 耗时、分片数、`markFailed` 原因打指标（Micrometer）与结构化日志。
5. **多租户**：若上线多租户，在 `kb_*` 表增加 `tenant_id` 并在所有查询强制带条件。

## MapStruct 与全项目

当前仅在 **KB 模块**引入 MapStruct 作为样板；其他上下文的 Assembler 若字段多、易出错，可按同样方式逐步替换，不必一次性全改。
