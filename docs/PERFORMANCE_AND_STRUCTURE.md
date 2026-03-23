# 性能与结构（全模块）

本文档与 `backend/ARCHITECTURE.md` 配套：**架构边界**见后者；本文聚焦**性能习惯、结构演进与反模式**，覆盖仓库内各业务模块（后端
Bounded Context + 前端 `app/` / `lib/`）。

---

## 1. 通用原则

| 原则        | 说明                                                                                                                    |
|-----------|-----------------------------------------------------------------------------------------------------------------------|
| **批量化**   | 列表页、聚合统计避免「逐行查库」；优先 `IN (...)` / 分组 SQL / 一次 `Map<id, count>`。                                                        |
| **单次解析**  | 同一请求内对同一外键（如 `vector_store_profile_id`）多次 `findById` 应合并为一次解析并复用（参考 `MemoryVectorIndexService` 的 `VectorResolution`）。 |
| **边界清晰**  | Web 只做校验与适配；规则在 `application` / `domain`；IO 在 `infrastructure`。不引入「上帝 Service」跨上下文直连实现类。                              |
| **前端数据流** | 页面经域 `api.ts` → `lib/api/request`；避免在组件内散落 `fetch` URL；长列表注意取消未完成的请求（`AbortSignal` / `useAbortController`）。           |
| **可观测**   | 长耗时路径打日志/指标；向量/外部 HTTP 失败应可区分超时与 4xx/5xx。                                                                             |

---

## 2. 后端按模块

### 2.1 `api`（横切）

- **异常**：`ApiException` + `GlobalExceptionHandler` 统一 JSON；勿在领域层吞掉异常后返回 null 造成静默失败。
- **性能**：异常处理直接写 `HttpServletResponse`，避免内容协商问题（已文档化）。

### 2.2 `agent`

- **结构**：智能体与工具多对多经 `lego_agent_tools`；创建/更新时批量写关联，避免 N 次单条插入（见现有 `insertAgentTools`
  模式）。
- **性能**：列表若需展示模型名/记忆策略名，优先批量查 `Model` / `MemoryPolicy` 或 JOIN，避免 `getAgent` 循环内查询。

### 2.3 `model` / `vectorstore`

- **结构**：聊天模型与嵌入模型分流（`ModelProvider.isChatProvider`）；向量 Profile 与物理集合绑定表独占，避免双写。
- **性能**：探测、连通性检查带超时；勿在请求线程阻塞无上限。

### 2.4 `kb`（知识库）

- **结构**：入库、分片、向量写入分层（`KbApplicationService` 编排 + `KbIngest*` + `KbVectorStore`）；多集合召回规则集中在
  `kb.support`。
- **性能**：大批量文档用异步/批处理；向量检索 `fetch` 上限与 `topK` 成比例；避免在循环内 `embed` 单条（批嵌入优先）。
- **性能**：正文 `{{tool:…}}` / 富文本 `tool_field` 解析时，对 `linkedToolIds` 使用 `ToolRepository.findByIds` 预加载并在
  `KbKnowledgeInlineToolSyntax` 内复用，避免按绑定 ID 循环 `findById`。

### 2.5 `memorypolicy`

- **结构**：策略与条目表分离；删策略先清外置向量再删 PG（与 `MemoryVectorIndexService.purgeAllVectorsForPolicy` 一致）。
- **性能**：`resolveVectorContext` 单次解析 Profile + 集合配置；`reindex-vectors` 为运维补偿接口，大量条目时后续可考虑批嵌入。

### 2.6 `tool` / `mcp`

- **结构**：工具执行统一走 `ToolExecutionService`；HTTP 工具使用 OkHttp 与 SSRF 守卫（见 `ARCHITECTURE.md`）。
- **性能**：HTTP/MCP 调用必须设连接与读超时；避免在同步请求中无限等待。
- **性能**：`/tools/{id}/references` 对智能体侧用单次 SQL（窗口函数）返回「引用总数 + 样本 id」，避免 `count` + `list` 双查；MCP
  批量导入在预检同名后调用 `createTool(..., skipNameCheck)`，避免每条再 `exists` 一次。

### 2.7 `workflow` / `eval` / `a2a`

- **结构**：运行记录与评测配置分表；A2A 网关薄封装，复用 `AgentApplicationService`。
- **性能**：长流程应落库状态并支持轮询/流式；避免单次 HTTP 持有整个工作流生命周期（视部署调整超时）。

### 2.8 `runtime`

- **结构**：`AgentRuntime` 为推理门面，避免业务模块绕过它直接 new AgentScope 对象。
- **性能**：Reactor 链路易阻塞点用 `boundedElastic`；大消息注意背压。

---

## 3. 前端（`frontend/`）

| 区域                          | 结构                     | 性能                                   |
|-----------------------------|------------------------|--------------------------------------|
| **`lib/api/request.ts`**    | 统一入口、类型与错误码            | 支持 `timeoutMs` 与 `signal` 组合，防止悬挂请求  |
| **域 `lib/<domain>/api.ts`** | 唯一对外 API 面             | 列表/详情参数类型化，避免重复拼 query               |
| **`app/**/page.tsx`**       | 容器组件 + 局部表单            | 路由切换时取消 in-flight 请求；大表虚拟滚动（按需）      |
| **`hooks/`**                | `useAbortController` 等 | 卸载时 abort，减少 setState on unmount 类问题 |

---

## 4. 数据库与迁移

- 见 `docs/DB_OPTIMIZATION.md`：索引、JSONB 查询、外键与批量删除顺序。
- 新表优先在迁移中声明必要索引；大表变更走在线 DDL 策略（生产另行规范）。

---

## 5. 迭代检查清单（发版前可扫）

- [ ] 新增列表接口是否 N+1？
- [ ] 同一外键在一次请求内是否重复加载？
- [ ] 外部 HTTP/向量/模型调用是否有超时？
- [ ] 前端是否暴露未取消的请求？
- [ ] 大 JSON 是否在热路径重复 `parse`？

---

## 6. 与代码变更的对应关系

- **单次向量上下文解析**：`MemoryVectorIndexService` 中 `VectorResolution` / `resolveVectorContext`。
- **前端默认超时**：`lib/api/request.ts` 中 `timeoutMs` 与 `DEFAULT_REQUEST_TIMEOUT_MS`。
- **可取消请求示例**：`hooks/useAbortController.ts`；**记忆策略详情**（`app/memory-policies/[id]/page.tsx`）在 `policyId`
  变化或卸载时对 `getMemoryPolicy` / `listReferencingAgents` / `listMemoryItems` 使用 `AbortController`，并用
  `lib/api/isAbortError.ts` 忽略中止错误。
- **记忆策略列表**（`app/memory-policies/page.tsx`）：`listMemoryPolicies` / 弹窗内 `listVectorStoreProfiles` 带 `signal` +
  `DEFAULT_REQUEST_TIMEOUT_MS`；创建/更新/删除策略传 `timeoutMs`。
- **智能体详情**（`app/agents/[id]/page.tsx`）：`getAgent` / `updateAgent` 使用 `timeoutMs`；路由切换时 `getAgent` 用
  `AbortController`；`runAgent` 不设默认超时，仅用 `signal`（卸载或新一次试运行会中止上一次），`isAbortError` 忽略中止。
- **记忆策略 API**：`lib/memory-policies/api.ts` 各方法支持可选 `signal` / `timeoutMs`（`MemoryPolicyFetchOpts`）；
  `listMemoryPolicies` 已接入 `opts`。
- **模型 API**：`lib/models/api.ts` 使用 `ModelFetchOpts`（`signal` / `timeoutMs`）；列表页首屏与详情加载、创建/更新/删除/测试按页面组合使用。
- **工具 API**：`lib/tools/api.ts` 的 `listToolsPage(params, opts)` 与元数据拉取支持 `ToolFetchOpts`。
- **知识库**：`listKbCollections(opts?)` 使用 `KbFetchOpts`（与 `useAgentFormRefs` 并行加载一致）。
- **工作流 / 运行 / 评测**：`getWorkflow` 带超时；`runWorkflow` 仅 `signal`（长任务不设默认超时）；`getWorkflowRun` /
  `getEvaluationRun` 在轮询页用 `AbortController` 取消过时请求。
- **向量库**：`createVectorStoreProfile` / `deleteVectorStoreProfile` / `probeVectorStoreProfile` 支持
  `VectorStoreFetchOpts`；运维面板 `VectorStoreOpsPanel` 对列表/探测/集合/预览等请求统一 `timeoutMs`。
- **知识库**：`lib/kb/api.ts` 已全部使用 **`KbFetchOpts`**（含创建/入库/更新/删除/校验/召回/渲染等）；`app/kb/page.tsx`
  使用模块级 **`KB_REQ`** 与 `signal` 合并；`KbIngestDocumentDrawer` 对 `get`/`ingest`/`update` 传同一超时。
- **工具详情**：`app/tools/[id]/page.tsx` 对 `getTool` / 元数据 / `fetchToolReferences` 使用共享 `AbortController`；
  `testToolCall` 仅 `signal`；抽屉/批量导入/MCP 发现等组件对写操作设 `timeoutMs`。
- **A2A / 评测创建**：`delegateA2a` 使用 `A2aFetchOpts`（长任务不设默认超时，仅用 `signal`）；`createEvaluation` 设超时，
  `triggerEvaluationRun` 仅用 `signal`。
- **工作流列表**：`createWorkflow` 创建请求设 `timeoutMs`。

后续若某模块有专项优化（例如 KB 批量嵌入队列），在本文件对应小节追加「已实现」与 PR 链接即可。
