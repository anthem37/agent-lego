# 平台「记忆策略」领域说明

本文档定义 **Agent Lego** 中「记忆策略」与 **知识库 RAG** 的边界，以及各字段的**语义**（不是实现细节堆砌）。

## 1. 与知识库（RAG）的边界

| 能力   | 知识库 RAG           | 记忆策略                                |
|------|-------------------|-------------------------------------|
| 数据来源 | 文档入库、分块、向量索引      | 对话/运营写入的**条目**（`lego_memory_items`） |
| 典型用途 | 手册、政策、产品说明等「可查资料」 | 用户偏好、历史事实摘要、任务上下文等「可回忆信息」           |
| 产品心智 | 「查文档」             | 「记/回忆与本次对话或用户相关的事」                  |

二者可并存：智能体可同时绑定 **知识库策略** 与 **记忆策略 ID**。

## 2. 策略维度（一等语义）

### 2.1 `strategy_kind`（记忆目的）

- **`EPISODIC_DIALOGUE`**：对话中沉淀的事实、偏好、约定（情景记忆）。
- **`USER_PROFILE`**：相对稳定、可跨会话复用的画像类信息（仍落同一套条目存储，检索与治理规则可后续按 kind 区分）。
- **`TASK_CONTEXT`**：任务级上下文、阶段性结论（预留，可与 TTL/归档配合）。

### 2.2 `scope_kind`（`owner_scope` 怎么理解）

- **`CUSTOM_NAMESPACE`**：由你在控制台填写的 **全局唯一** 字符串，自行约定前缀（租户/业务线/场景），平台不解析结构。
- **`TENANT` / `USER` / `AGENT`**：预留；未来可与租户 ID、用户 ID、智能体 ID 绑定解析，当前仍以 `owner_scope` 存字符串。

### 2.3 `retrieval_mode`（检索策略）

- **`KEYWORD`**：PostgreSQL **子串匹配**（`ILIKE '%q%'`）；当查询字符串非空时，结果在匹配集内按 *
  *`pg_trgm` `word_similarity(q, content)`** 降序排序（粗略相关度），再辅以更新时间；需扩展 `pg_trgm`（见基线迁移）。
- **`VECTOR` / `HYBRID`**：在策略上配置 **公共向量库 Profile**（`vector_store_profile_id`）与合并后的 *
  *`vector_store_config_json`**（与知识库集合一致，含物理 `collectionName`；未填则默认 `mem_pol_{策略id}`）。运行时复用 *
  *`KbVectorStore`** + **`ModelEmbeddingClient`** 写入/检索向量；未配置完整时 **降级为 KEYWORD**（含上述 `pg_trgm` 排序）。

### 2.4 `write_mode`（写入策略）

- **`OFF`**：不把助手输出写回条目表。
- **`ASSISTANT_RAW`**：将助手最终文本写入（可配合 `write_back_on_duplicate`）。
- **`ASSISTANT_SUMMARY`**：**本地粗略摘要**后写入——按字符上限（策略可配置 `rough_summary_max_chars`，空则默认
  480）与句读/换行边界截断，**非**模型语义摘要；元数据中带 `summaryKind: ROUGH_CHAR_CAP`、`roughSummaryMaxChars`
  （实际采用的上限）等。若需 LLM 摘要需后续单独接入。

## 3. 运行时行为（AgentScope）

- 智能体绑定 `memory_policy_id` 后，挂载 `LongTermMemoryMode.STATIC_CONTROL`。
- **检索**：由 `retrieval_mode` 与 `top_k` 控制；向量类模式在未接好时降级。
- **写入**：由 `write_mode` 与去重策略控制。

## 4. 演进路线（摘要）

- 向量检索与 `VECTOR`/`HYBRID` 对齐实现。
- `ASSISTANT_SUMMARY` 可选接入 **LLM 语义摘要**（与当前本地 `ROUGH_CHAR_CAP` 并存或替换策略可配置）。
- `scope_kind` 与多租户账号体系打通。

## 5. 控制台闭环与 HTTP 约定

以下为 **一等策略** 落地后的产品/API 行为，便于联调与排障。REST
路径与字段速查见 [api-memory-policies.md](./api-memory-policies.md)。

### 5.1 智能体绑定与更新

- 表 `lego_agents.memory_policy_id` 外键引用 `lego_memory_policies(id)`（库级 `ON DELETE SET NULL`，但业务上见下条「删除约束」）。
- **创建**：`POST /agents`，请求体可选 `memoryPolicyId`。
- **全量更新**：`PUT /agents/{id}`，请求体与创建相同（名称、系统提示、模型、工具列表、知识库策略 JSON、`memoryPolicyId`、
  `runtimeKind`、`maxReactIters` 等），用于在智能体详情页**改绑/解绑**记忆策略。

### 5.2 策略侧「谁在用」

- **列表/详情聚合字段**：`MemoryPolicyDto.referencingAgentCount` — 当前有多少智能体的 `memory_policy_id` 指向该策略。
- **明细**：`GET /memory-policies/{id}/referencing-agents`，返回 `{ id, name }[]`，链到各智能体详情。

### 5.3 策略下条目查询

- `GET /memory-policies/{id}/items?q=&limit=`：关键词匹配 `content`（`ILIKE`）；可选 **`orderByTrgm=true`**（且 `q` 非空时生效）按
  `word_similarity` 排序，与智能体运行时关键词检索一致；默认不传则仅按时间排序。

### 5.4 删除策略

- 若 `referencingAgentCount > 0`，`DELETE /memory-policies/{id}` 返回 **409**，错误码 `MEMORY_POLICY_IN_USE`
  ，提示先在智能体侧改绑或解绑；**不会**依赖数据库静默 `SET NULL` 来完成删除。
- 无引用时，删除策略会按迁移约定级联清理其下条目等业务数据（以 Flyway/仓储实现为准）。
- **`VECTOR` / `HYBRID` 且向量库已正确配置**时，删除前会**先清理该策略下所有条目的外置向量**（与单条删除一致），避免仅删 PG
  后 Milvus/Qdrant 残留。
- 运维侧可对策略调用 **`POST /memory-policies/{id}/reindex-vectors`**
  ，在向量配置有效时对该策略下全部条目重新索引；详见 [api-memory-policies.md](./api-memory-policies.md)。

### 5.5 试运行时的记忆可观测性

- 策略 DTO（`MemoryPolicyDto`）与试运行 `memory` 均可能包含 **`implementationWarnings`**：当 `retrieval_mode` 为
  VECTOR/HYBRID 或 `write_mode` 为 ASSISTANT_SUMMARY 时，提示当前运行时与字面值差异（降级或未接摘要等），避免静默预期落差。
- 记忆条目在运行时写回时会在 **`metadata`** 中写入 **`strategyKind`**（与策略一致）；关键词检索与去重会排除
  `metadata.strategyKind` 与策略不一致的条目（缺省/空 `strategyKind` 的旧数据仍兼容）。
- **其他入口**：工作流 definition / 各 step 可设 **`memoryNamespace`**；评测创建请求可带 **`memoryNamespace`**（写入评测
  config，整次 run 共用）；A2A **`POST /a2a/delegate`** 请求体可选 **`memoryNamespace`**。均通过
  `AgentRunRequests.of(modelId, input, memoryNamespace)` 与直连 `POST /agents/{id}/run` 对齐。

### 5.6 试运行响应 `memory` 字段

- `POST /agents/{id}/run` 在智能体**已绑定**记忆策略时，响应除 `output` 外可带 **`memory`**（`AgentRunMemoryDebug`）：
    - 策略 id/名称、`owner_scope`、`retrieval_mode`、`write_mode`、`implementation_warnings`（与 5.5 同源）
    - **`rough_summary_max_chars_resolved`**（JSON 驼峰 `roughSummaryMaxCharsResolved`）：策略上粗略摘要字符上限解析后的实际值（与
      `MemoryRoughSummary.resolveMaxChars` 一致），便于与 ASSISTANT_SUMMARY 写回对齐
    - **`keywordPreviewSort`**：`RECENCY`（输入为空）或 `TRGM_WORD_SIMILARITY`（输入非空时与预览列表排序一致）
    - 可选请求体字段 **`memoryNamespace`**（非空字符串）：写入条目时落到 `metadata.memoryNamespace`
      ，检索与去重仅在「同一策略 + 同一命名空间」内进行；不传或空字符串表示与**未带该字段的历史条目**共用（全局池）。
    - 响应中回显本次使用的 **`memoryNamespace`**（未传则为 `null`）。
    - 用**当前用户输入**按策略 `top_k` 做与运行时一致的关键词预查：`preview_hit_count`、`preview_text`（截断展示）
- 未绑定策略时 `memory` 省略或为 `null`。
