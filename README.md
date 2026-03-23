# agent-lego

智能体乐高

## MCP

- **本服务作为 MCP Server（对外暴露工具）**：默认启用 WebMvc 传输，路径见 `application.yml` →
  `agentlego.mcp.server.sse-path`（默认 `/mcp`）：**`GET /mcp` 为 SSE**，**`POST /mcp/message`** 为 JSON-RPC 消息（由 MCP SDK
  在 SSE 的 `endpoint` 事件中下发，客户端一般只需配置 SSE 根 URL）。工具列表与进程内 **LOCAL 内置工具**（
  `LocalBuiltinToolCatalog` 从 `LocalBuiltinTools` 读取 `@Tool` 暴露结果，并经 `lego_local_builtin_tool_exposure` 过滤）一致。
  默认内置示例：`time_now`（当前时间）、`text_transform`（文本变换）、`uuid_generate`（UUID）、`json_format`（JSON 美化/压缩）、`hash_sha256`（SHA-256 hex）。
- **本服务调用外部 MCP（作为 Client）**：在工具管理中新建类型 **MCP** 的工具，`definition.endpoint` 填外部 MCP 的 **SSE URL
  **（完整地址，需与对端文档一致）。可选 `definition.mcpToolName` 指定远端 `tools/call` 的工具名；省略则使用平台登记的
  `name`。
- **批量导入远端 MCP 工具**：工具列表页「MCP 批量导入」，或新建 MCP 工具时「从该 SSE 地址发现并批量导入」。后端接口：
  `GET /tools/meta/mcp/remote-tools?endpoint=...`、`POST /tools/meta/mcp/batch-import`。安全：生产可设
  `agentlego.mcp.client.strict-ssrf=true`（与 HTTP 工具一致禁止内网地址；本地联调保持 `false`）。
- 关闭内置 MCP Server：`agentlego.mcp.server.enabled=false`。

## 数据库与 Flyway

- 连接串等见 `backend/src/main/resources/application.yml`（`spring.datasource`）。
- 迁移脚本在 `backend/src/main/resources/db/migration/`；应用启动默认执行 Flyway（可用 `FLYWAY_ENABLED=false` 关闭）。
- **知识库**：PostgreSQL 仅存 **`lego_kb_collections` / `lego_kb_documents`** 元数据；分片向量在 **Milvus**（
  `vector_store_config`）。
  全新库仅一条 Flyway 脚本 **`V1__baseline.sql`**（**不**安装 pgvector）。本地 PostgreSQL：
  `docker compose -f docker-compose.postgres.yml up -d`。
  详见 `docs/KB_REDESIGN.md`、`backend/ARCHITECTURE.md`。
- **性能与结构（全模块）**：清单与演进约定见 [`docs/PERFORMANCE_AND_STRUCTURE.md`](docs/PERFORMANCE_AND_STRUCTURE.md)（与
  `backend/ARCHITECTURE.md` 配套）。
- 若库中已有 **旧版** Flyway 历史（曾存在 V2–V9），与本仓库 **仅 V1** 的脚本不兼容：请**新建空库**或清空数据卷后启动，勿对生产旧库直接替换迁移文件。
- 若仅改过 checksum（例如基线 `V1__baseline.sql` 有调整），可在 `backend` 执行 `mvn flyway:repair`（全新库一般不需要）。
