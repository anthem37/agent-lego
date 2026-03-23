# 记忆策略（前端）

- `api.ts`：策略 CRUD、条目、`listReferencingAgents`；`listMemoryItems` 支持 `orderByTrgm`（有关键词时与运行时排序一致）；
  `MemoryPolicyDto.implementationWarnings` 与后端对齐；`reindexMemoryPolicyVectors` 对应
  `POST /memory-policies/{id}/reindex-vectors`。
- 策略详情页 `app/memory-policies/[id]/page.tsx`：VECTOR/HYBRID 时展示「重索引向量」按钮（向量链路未配置时为禁用态）；条目表展示
  `metadata` 中的 `summaryKind`（如粗略摘要）、`strategyKind`、`memoryNamespace`、`roughSummaryMaxChars`（写回时采用的上限）等标签。
- 策略表单支持 `roughSummaryMaxChars`（16～8192，空则后端默认 480）；对应列 `lego_memory_policies.rough_summary_max_chars`。*
  *编辑**时可勾选「清除已保存的摘要上限」，对应 `PUT` 请求体 `clearRoughSummaryMaxChars: true`。
- VECTOR/HYBRID 时可选公共向量库 Profile、物理集合名覆盖、`vectorMinScore`；与知识库集合共用 `/vector-store-profiles` 与
  `KbVectorStore` 写入路径。
- `semantics.ts`：strategyKind / retrievalMode / writeMode 等展示与选项。
