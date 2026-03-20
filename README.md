# agent-lego

智能体乐高

## MCP

- **本服务作为 MCP Server（对外暴露工具）**：默认启用 SSE 路由，路径见 `application.yml` → `agentlego.mcp.server.sse-path`（默认 `/mcp`）。工具列表与进程内 **LOCAL 内置工具**（`LocalBuiltinToolCatalog` 扫描结果）一致，执行逻辑与平台 LOCAL 工具相同。
- **本服务调用外部 MCP（作为 Client）**：在工具管理中新建类型 **MCP** 的工具，`definition.endpoint` 填外部 MCP 的 **SSE URL**（完整地址，需与对端文档一致）。可选 `definition.mcpToolName` 指定远端 `tools/call` 的工具名；省略则使用平台登记的 `name`。
- 关闭内置 MCP Server：`agentlego.mcp.server.enabled=false`。
