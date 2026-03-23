# API 与 DTO 约定（前后端对齐）

## 响应包装

- HTTP JSON 使用项目统一的 **`ApiResponse<T>`**（`success` / `data` / `message` 等，以实际 `api` 包为准）。
- 前端 `request()` 已按约定解包 `data`。
- 工具 API：`/tools/*` 由 `ToolController`（CRUD、分页、引用、test-call）与 `ToolMetaController`（`/tools/meta/**` 类型/分类/内置/MCP
  发现与批量导入）分担，**路径与原先一致**。

## 命名与序列化

- JSON 字段采用 **camelCase**（Jackson 默认）。
- 领域表列可为 `snake_case`，由 MyBatis / 映射层转为 DO，再经 MapStruct 转为 DTO。

## 知识库文档 `KbDocumentDto`

- 列表接口可为性能省略大字段（如 `body`）；**详情 GET** 返回完整正文与 **`similarQueries`**（若库中有持久化）。
- 入库/更新请求体 `IngestKbDocumentRequest` 与前端 `IngestKbDocumentBody` 字段一一对应。

## 工具引用 `ToolReferencesDto`（`GET /tools/{id}/references`）

- **`referencingAgentCount`**：经 `lego_agent_tools` 引用该工具记录的智能体**总数**。
- **`referencingAgentIds`**：样本智能体 id（条数有上限，按创建时间倒序），用于控制台提示。
- **`referencingKbDocumentCount`**：知识库文档 `linked_tool_ids`（jsonb）中含该工具 id 的文档数；删除工具前需无智能体与 KB
  引用（后端 `DELETE /tools/{id}` 会校验）。

## 扩展新字段

1. 后端 DTO + MapStruct 映射（必要时 `@JsonInclude` 仅作用于需要省略的字段）。
2. 前端 `lib/**/types.ts` 同步类型。
3. 避免在 Controller 手写 Map，优先 DTO 保证契约稳定。
