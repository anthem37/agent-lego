# Agent Lego 后端架构说明（DDD 与工程约定）

## 包与领域边界（约定）

- **Bounded Context**：`agent` / `workflow` / `eval` / `kb` / `tool` / `model` / `vectorstore` 等顶层包即边界；同上下文内保持
  `domain` → `application` → `infrastructure`，**禁止**领域层依赖 Spring/MyBatis/HTTP。
- **Web 入口**：各上下文 `*.web` 包（如 `agent.web.AgentController`）；横切探针在 `api.web.HealthController`。仅做 HTTP
  适配与校验注解，不写业务规则。
- **`mcp`（Model Context Protocol）**：`mcp.properties`（`@ConfigurationProperties`）、`mcp.client`（`McpClientRegistry`）、
  `mcp.support`（Schema/响应映射）、`mcp.adapter`（本机 Server 装配 + 外连 Client 构建）、`mcp.config`（Spring 路由与生命周期）。
- **`runtime`**：智能体/工具推理运行时门面，被 `agent` / `tool` 等调用。
- **`a2a`**：`a2a.web`（REST）、`a2a.service`（`A2AGatewayService` 应用服务）、`a2a.dto`，与 `agent.application.service` 协作。

## 分层（Bounded Context 内）

| 包路径                            | 职责                                                                                      |
|--------------------------------|-----------------------------------------------------------------------------------------|
| `*.domain`                     | 聚合根、值对象、仓储接口（**无** Spring / MyBatis / HTTP 依赖）                                          |
| `*.application.service`        | 应用服务：用例编排、事务边界（`*ApplicationService`、`ToolExecutionService`）                            |
| `*.application.dto` / `mapper` | DTO 与 MapStruct 映射，与领域解耦的出入参形状                                                          |
| `*.web`                        | REST `*Controller`，依赖 `application.service` + `api.ApiResponse`                         |
| `*.infrastructure`             | 仓储实现、MyBatis Mapper、外部系统适配                                                              |
| `api`                          | 对外契约：`ApiException`、`ApiRequires`（含 `nonBlankTrimmed` 等与知识库一致的校验文案）、统一响应与全局异常（与 Web 相邻） |
| `common`                       | 共享内核：Snowflake、`JsonMaps`、`Throwables`/`EnumStrings`、无业务语义                              |
| `runtime`                      | **推理运行时门面**：对话智能体、模型工厂装配，供 `agent` / `tool` 等上下文复用                                      |

### 各上下文的 DTO 映射（MapStruct：`*.application.mapper`）

| 包                             | 接口                                                                                                                                                                                                                                                            |
|-------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `agent.application.mapper`    | `AgentDtoMapper`                                                                                                                                                                                                                                              |
| `eval.application.mapper`     | `EvaluationRunDtoMapper`                                                                                                                                                                                                                                      |
| `model.application.mapper`    | `ModelDtoMapper`                                                                                                                                                                                                                                              |
| `tool.application.mapper`     | `ToolDtoMapper`                                                                                                                                                                                                                                               |
| `tool.application.service`    | `ToolApplicationService`（分页/详情/更新/删除/引用/test-call 门面）、`ToolCreationService`（创建）、`ToolConsoleMetaService`（类型/分类/内置元数据）、`McpToolDiscoveryService`（远端 tools/list 与批量导入）、`ToolExecutionService`（运行时 Toolkit）                                                      |
| `tool.application.support`    | `ToolWriteSupport`、`LocalToolResponseEnricher`（LOCAL 详情/列表补全 inputSchema/outputSchema）、`ToolConsoleMetaConstants`（控制台元数据编码常量）、`McpPlatformToolNaming`（MCP 导入平台名规则）                                                                                            |
| `tool.web`                    | `ToolController`（`/tools` CRUD / 分页 / 引用 / test-call）、`ToolMetaController`（`/tools/meta/**` 元数据与 MCP 发现）                                                                                                                                                      |
| `workflow.application.mapper` | `WorkflowDtoMapper`                                                                                                                                                                                                                                           |
| `kb.application.mapper`       | `KbDtoMapper`                                                                                                                                                                                                                                                 |
| `kb.application.service`      | `KbApplicationService`（编排入口）、`KbCollectionCommandService`（集合创建）、`KbCollectionAccess`（集合/文档存在性）、`KbIngestPayloadPreparer` / `KbIngestFinalizeRunner`（入库正文校验与分片向量写入）、`KbVectorRetrieveRunner`（召回预览用的向量检索）；召回命中 DTO 由 `kb.support.KbRetrievePreviewAssembler` 组装 |
| `kb.application.validation`   | `KbDocumentValidator`（工具绑定与正文校验，不写库）                                                                                                                                                                                                                          |
| `kb.rag`                      | `KbVectorRetrieve` / `KbVectorRetrieveEngine`、`KbRagKnowledgeFactory`、`KbRetrievedChunkRenderer`、会话工具出参等                                                                                                                                                      |
| `kb.support`                  | 分片、富文本/Markdown、工具绑定、策略常量、**多集合召回规则**（`KbMultiRetrievePreviewRules`）、召回预览组装（`KbRetrievePreviewAssembler`）等（见 `package-info`）                                                                                                                                  |
| `kb.vector`                   | `KbVectorStore`、`DelegatingKbVectorStore`、`KbVectorStoreConfigMaps`（运行时空配置校验）                                                                                                                                                                                 |

## 知识库（Milvus / Qdrant）

- **物理集合独占**：`lego_vector_store_collection_bindings` 记录 `(profile_id, physical_collection_name)` →
  知识库集合；同一 profile 下可有多个物理 collection，各自独立绑定。
- **模型**：`lego_kb_collections`（含 `vector_store_kind`、`vector_store_config` jsonb、`embedding_model_id`、
  `embedding_dims`、分片策略）→
  `lego_kb_documents`；**分片向量仅存外置向量库**（每集合一个物理 collection；`MILVUS` 或 `QDRANT`）。全新库仅 *
  *`V1__baseline.sql`**，无 `lego_kb_chunks`、无 pgvector。
- **运行时（分层）**：`kb.vector.DelegatingKbVectorStore`（`@Primary`）按 `vector_store_kind` 路由到
  `kb.milvus.MilvusKnowledgeStore` 或 `kb.qdrant.QdrantVectorStore`；`kb.rag.KbVectorRetrieveEngine` 跨多集合合并候选；
  `kb.rag.KbRetrievedChunkRenderer` 在注入模型前按文档绑定展开 `{{tool:…}}`、用 `KbToolPlaceholderExpander` 替换
  `tool_field`（数据来自单次请求的
  `KbRagSessionToolOutputs.forExpansion()`）。`KbVectorKnowledge` 实现 agentscope `Knowledge`；
  `kb.rag.KbRagKnowledgeFactory` 按智能体策略装配。
  `runAgent` 在绑定知识库时创建 `KbRagSessionToolOutputs`，
  `ToolExecutionService.buildToolkitForToolIds(..., sessionToolOutputs)` 对 HTTP/MCP/WORKFLOW 的 `AgentTool` 做成功结果录制。
- **向量化**：`ModelEmbeddingClient` + `ModelEmbeddingDimensions.fitToCollectionDim`（与集合声明维度对齐，上限
  `VECTOR_STORE_MAX_DIM`）。
- **应用层约定**：可复用的文档规则放在 `KbDocumentValidator`；`KbApplicationService` 只做编排与边界（集合/文档存在性仍为私有方法）；
  多集合召回约束与预览命中 DTO 分别由 `KbMultiRetrievePreviewRules`、`KbRetrievePreviewAssembler` 承担，避免应用服务膨胀。
  **不**为纯转发增加 Bean/包，**不**保留空目录。

## 智能体与模型

- **工具绑定**：持久化为 `lego_agent_tools(agent_id, tool_id)`（外键指向 `lego_tools`），不再使用
  `lego_agents.tool_ids`
  数组；详见 Flyway `V1__baseline.sql` 与 `docs/DB_OPTIMIZATION.md`。
- **模型类型**：`ModelProvider` 区分为 **聊天**（`DASHSCOPE` / `OPENAI` / `ANTHROPIC`，走对话与采样参数）与 **文本嵌入**
  （`*_TEXT_EMBEDDING`，仅 `dimensions` + `executionConfig` + `baseUrl`/`apiKey`）。智能体创建与运行要求绑定聊天类；知识库向量化要求嵌入类。
  `GET /models` 返回
  `chatProvider`；`GET /models/providers` 返回 `modelKind`（`CHAT` | `EMBEDDING`）。
- **对话与工具执行**：统一经 `AgentRuntime` + `ChatModelFactory` 装配运行时模型与工具集。
- **工具名**：运行时以工具 **name** 为键；平台侧仅承认 **全平台 name 唯一（大小写不敏感）**（`ux_lego_tools_name_lower` +
  应用层 `requireGloballyUniqueToolName`），不单独维护「按 toolType 分区」的唯一性（Flyway `V7` 已移除冗余的
  `UNIQUE (tool_type, name)`）。LOCAL 为进程内内置工具，HTTP/MCP/WORKFLOW 为
  可调用代理工具（参数以 JSON Schema 子集描述，与模型侧工具调用约定对齐）。
  内置 LOCAL 由 `LocalBuiltinTools`（`@Component`）承载 `@Tool` 方法，`LocalBuiltinToolCatalog` 解析元数据。
  `LocalBuiltinJsonSchemaBuilder` 生成与 HTTP 工具一致的 `definition.inputSchema` / `outputSchema`（JSON Schema
  子集），供控制台表格展示。
- **删除与引用**：`ToolApplicationService.deleteTool` 拒绝删除仍被 `lego_agent_tools` 引用或知识库文档
  `linked_tool_ids`（jsonb 数组含该工具 id）引用的工具；`GET /tools/{id}/references` 一次性汇总智能体引用（总数 + 样本 id）与
  KB 文档数。
- **HTTP 工具出站**：使用 Square **OkHttp**（`OkHttpHttpToolRequestExecutor`），不直接使用 `java.net.http.HttpClient`
  ；连接/整次调用超时见
  `agentlego.tool.http-connect-timeout-seconds` / `http-call-timeout-seconds`。URL 安全策略为可注入的
  `HttpToolUrlValidator`（默认
  `SsrHttpToolUrlValidator` → `SsrUrlGuard`）。
- **HTTP 工具扩展**：实现 `HttpToolOkHttpInterceptor`（继承 OkHttp `Interceptor`）并注册为 Spring Bean，即可挂入专用客户端；可选开启
  `agentlego.tool.http-request-logging=true` 使用内置 `Slf4jHttpToolOkHttpInterceptor`。执行结果 → `ToolResultBlock`
  的纯映射集中在
  `HttpToolResultMapper`，`HttpProxyAgentTool` 保持单薄。
- **聊天模型持久化 config**：键名以 `ModelProvider.supportedConfigKeys()` 为白名单，并在
  `ChatModelFactory.buildGenerateOptions` 中映射到运行时生成参数（含 `stream`、`frequencyPenalty`/
  `presencePenalty`、`thinkingBudget`、`reasoningEffort`、`toolChoice`、`executionConfig` 等）；`executionConfig` 子键仅支持
  `timeoutSeconds`、`maxAttempts`、`initialBackoffSeconds`、`maxBackoffSeconds`、`backoffMultiplier`。`apiKey`/`baseUrl`/
  `modelName` 等由平台顶层字段提供，不放在 config Map 中。
- **通义 Embedding**：使用 DashScope 文本嵌入实现时，须在 `pom.xml` 显式依赖
  `com.alibaba:dashscope-sdk-java`；核心推理依赖未传递该 JAR。
- **禁止**在应用服务中直接对接各厂商 HTTP SDK（除非运行时暂无能力且已单独评审）。

## 记忆（AgentScope）

- **会话内上下文**：`AgentRuntime` 每次构建 `ReActAgent` 时使用 AgentScope `InMemoryMemory`，承载单次 `runAgent` 请求内的多轮
  tool/assistant 消息，**不落库**、不跨请求。

- **长期记忆（平台一等能力）**：智能体可绑定 `memory_policy_id`；运行时通过 `LegoLongTermMemory` 实现 AgentScope
  `LongTermMemory`，条目落库
  `lego_memory_items`（策略表 `memory_policies`）。检索模式含 KEYWORD（`pg_trgm` 的 `word_similarity` 排序可选）、VECTOR/HYBRID（配置
  `vector_store_profile_id` +
  合并后的 `vector_store_config_json` 后复用 **`KbVectorStore`** + **`ModelEmbeddingClient`**；未配置完整时降级 KEYWORD）；
  写回含 `ASSISTANT_SUMMARY`（本地字数截断类粗略摘要，非 LLM 语义摘要）。物理集合独占见
  `lego_vector_store_collection_bindings.memory_policy_id`。
  单次请求可传 `memoryNamespace`（写入 `metadata`，检索/去重与命名空间 + `strategyKind` 条件一致）。

- **向量库运维**：REST `/vector-store-profiles` CRUD；知识库集合引用 `vector_store_profile_id`。
- **运维 API**（`VectorStoreOperationsController`）：`GET /{id}/probe`、`GET /{id}/usage`（知识库引用）、
  `GET /{id}/collections` 等见控制器。

## 重复代码与三方库

- Map/JSON 字段读取：优先 `JsonMaps`（Hutool `MapUtil` + Jackson）。
- 异步/落库失败文案：`Throwables.messageOrSimpleName`；枚举 → DTO 字符串：`EnumStrings.nameOrNull`（MapStruct `imports`）。
- 字符串/集合：优先 Hutool `StrUtil` / `CollUtil`。
- 应用层「非空字符串」校验：统一 `ApiRequires.nonBlank`。
- 配置 Map 浅合并：统一 `JsonMaps.shallowMerge`。
- 跨上下文复用「单次 runAgent 入参」：统一 `AgentRunRequests.of(modelId, input)` 或 `of(modelId, input, memoryNamespace)`
  （工作流 definition / 评测 config / A2A 可选传入命名空间）。

## 测试

- 应用服务用 Mockito 注入端口（仓储、工厂）；`EnvVariables` 等抽象便于替换环境依赖。
- Web 切片测试优先使用 `@MockitoBean`（`org.springframework.test.context.bean.override.mockito.MockitoBean`）替代已弃用的
  `@MockBean`。

## 性能与结构（全模块）

- 各边界上下文在**批量化查询、避免 N+1、外部调用超时、单次解析复用**等方面的约定与检查清单，见仓库根目录
  [`docs/PERFORMANCE_AND_STRUCTURE.md`](../docs/PERFORMANCE_AND_STRUCTURE.md)。
