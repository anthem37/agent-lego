# agent-lego

智能体乐高

## MCP

- **本服务作为 MCP Server（对外暴露工具）**：默认启用 WebMvc 传输，路径见 `application.yml` →
  `agentlego.mcp.server.sse-path`（默认 `/mcp`）：**`GET /mcp` 为 SSE**，**`POST /mcp/message`** 为 JSON-RPC 消息（由 MCP SDK
  在 SSE 的 `endpoint` 事件中下发，客户端一般只需配置 SSE 根 URL）。工具列表与进程内 **LOCAL 内置工具**（
  `LocalBuiltinToolCatalog` 扫描结果）一致。
- **本服务调用外部 MCP（作为 Client）**：在工具管理中新建类型 **MCP** 的工具，`definition.endpoint` 填外部 MCP 的 **SSE URL
  **（完整地址，需与对端文档一致）。可选 `definition.mcpToolName` 指定远端 `tools/call` 的工具名；省略则使用平台登记的
  `name`。
- **批量导入远端 MCP 工具**：工具列表页「MCP 批量导入」，或新建 MCP 工具时「从该 SSE 地址发现并批量导入」。后端接口：
  `GET /tools/meta/mcp/remote-tools?endpoint=...`、`POST /tools/meta/mcp/batch-import`。安全：生产可设
  `agentlego.mcp.client.strict-ssrf=true`（与 HTTP 工具一致禁止内网地址；本地联调保持 `false`）。
- 关闭内置 MCP Server：`agentlego.mcp.server.enabled=false`。
- **默认 MCP 工具（Flyway V4）**：迁移会插入 3 条示例工具（`mcp_local_echo` / `mcp_local_now` / `mcp_local_format_line`），
  `endpoint` 默认为 `http://127.0.0.1:8080/mcp`，通过 MCP 调用本服务已暴露的内置工具。端口或主机不同时请在工具管理中修改；已存在同名工具时不会覆盖（`
  ON CONFLICT DO NOTHING`）。

## 数据库与 Flyway

- 连接串等见 `backend/src/main/resources/application.yml`（`spring.datasource`）。
- 迁移脚本在 `backend/src/main/resources/db/migration/`；应用启动默认执行 Flyway（可用 `FLYWAY_ENABLED=false` 关闭）。
- **知识库 v3**：**`V18`** 起重建 `kb_*` 与 `knowledge_base_policy`；**`V19`** 启用 **pgvector**（
  `CREATE EXTENSION vector` + `kb_chunks.embedding_vec` + HNSW）；**`V20`** 从旧 **`embedding` jsonb** 回填 *
  *`embedding_vec`**（见 `docs/KB_REDESIGN.md`）。PostgreSQL 需支持并允许安装 `vector` 扩展；本地可选用 *
  *`docker-compose.postgres-pgvector.yml`**。
- 历史清理：`V15` 占位；**`V16`** 曾 `DROP` 旧 `kb_*`；**`V17`** 曾删除 `knowledge_base_policy`（已由 `V18` 恢复）。若曾修改过历史脚本
  checksum，需在 `backend` 目录执行 `mvn flyway:repair ...` 后再迁移。
