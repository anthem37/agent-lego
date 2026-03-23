# 记忆策略 HTTP API（联调参考）

基路径：`/memory-policies`（与平台其它接口相同前缀规则，响应包在统一 `ApiResponse` 中，此处只列业务体）。

## 策略 CRUD

| 方法       | 路径                      | 说明                                                                                              |
|----------|-------------------------|-------------------------------------------------------------------------------------------------|
| `GET`    | `/memory-policies`      | 策略列表；项中含 `referencingAgentCount`、`implementationWarnings`、`roughSummaryMaxChars`（可空）等           |
| `GET`    | `/memory-policies/{id}` | 策略详情                                                                                            |
| `POST`   | `/memory-policies`      | 创建；`201` 返回策略 id 字符串                                                                            |
| `PUT`    | `/memory-policies/{id}` | 全量更新（名称、owner_scope 等必填字段同创建）                                                                   |
| `DELETE` | `/memory-policies/{id}` | 删除；仍有智能体绑定时 `409` `MEMORY_POLICY_IN_USE`；若策略为 VECTOR/HYBRID 且已配置向量库，**会先清理该策略下所有条目的外置向量**再删库表记录 |

### 创建/更新请求体（节选）

- `roughSummaryMaxChars`（可选，整数）：`ASSISTANT_SUMMARY` 时本地粗略摘要最大字符数，范围由后端规范为 16～8192；**创建**
  时省略表示库中不存单独上限（运行时用默认 480）。
- **仅 `PUT`**：`clearRoughSummaryMaxChars`（可选，布尔）。为 `true` 时将库中 `rough_summary_max_chars` 置为 `null`，运行时使用默认
  480；与 `roughSummaryMaxChars` 同时出现时**以清除为准**（忽略本次传入的数字）。

## 引用智能体

| 方法    | 路径                                         | 说明               |
|-------|--------------------------------------------|------------------|
| `GET` | `/memory-policies/{id}/referencing-agents` | `{ id, name }[]` |

## 策略下条目

| 方法       | 路径                                     | 说明                                                                      |
|----------|----------------------------------------|-------------------------------------------------------------------------|
| `GET`    | `/memory-policies/{id}/items`          | 查询参数：`q`、`limit`、`orderByTrgm`（`q` 非空且为 `true` 时按 `word_similarity` 排序） |
| `POST`   | `/memory-policies/{id}/items`          | 手动写入 `{ content, metadata? }`                                           |
| `DELETE` | `/memory-policies/{id}/items/{itemId}` | 删除条目                                                                    |

领域语义见 [memory-strategy.md](./memory-strategy.md)。

### 向量检索（VECTOR / HYBRID）

- 创建/更新请求体可选：`vectorStoreProfileId`（公共向量库 `lego_vector_store_profiles.id`）、`vectorStoreConfig`（仅允许
  `{ "collectionName": "..." }` 覆盖物理集合名）、`vectorMinScore`（0～1）。
- 更新时可传 `clearVectorLink: true` 清空绑定与配置。
- 表 `lego_vector_store_collection_bindings` 通过 `memory_policy_id` 独占物理集合（与知识库 `kb_collection_id` 二选一）。

| 方法     | 路径                                      | 说明                                                                                                                           |
|--------|-----------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| `POST` | `/memory-policies/{id}/reindex-vectors` | 仅当策略为 VECTOR/HYBRID **且** 向量库已正确配置时执行；对该策略下全部记忆条目重新写入向量索引。响应体：`{ "indexedCount": number }`（未执行索引时可能为 `0`，例如仅 KEYWORD 或未配置向量） |

## 与智能体试运行的关系

- `POST /agents/{id}/run` 在绑定记忆策略时，响应体 `memory`（`AgentRunMemoryDebug`）中含 **`roughSummaryMaxCharsResolved`**
  等字段，与策略表 `rough_summary_max_chars`
  解析规则一致；详见 [memory-strategy.md §5.6](./memory-strategy.md#56-试运行响应-memory-字段)。
