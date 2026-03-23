# `frontend/lib` 模块索引

业务页面**不直接**调用 `@/lib/api/request`，统一经各域 `api.ts`（便于 mock、改基路径、类型收敛）。

| 目录                 | 说明                                                    |
|--------------------|-------------------------------------------------------|
| `agents/`          | 智能体：类型、`buildUpsertAgentRequestBody`、`api`、表单选项       |
| `models/`          | 模型配置 CRUD、探测、`listModelsAsSelectRows`                 |
| `tools/`           | 工具 CRUD、元数据、MCP、`testToolCall`                        |
| `kb/`              | 知识库 API；另含 `bootstrap`（首屏并行加载）、`page-helpers`（控制台纯函数） |
| `memory-policies/` | 记忆策略与条目                                               |
| `workflows/`       | 工作流创建与运行                                              |
| `runs/`            | 工作流运行详情                                               |
| `evaluations/`     | 评测创建与运行                                               |
| `a2a/`             | A2A delegate 调试                                       |
| `vector-store/`    | 向量库 Profile 运维                                        |
| `api/`             | `request` 封装、通用类型                                     |

共享 UI 数据加载见项目根目录 `hooks/`（如 `useAgentFormRefs`、`useAbortController`）；API 层见 `api/request.ts`（支持
`timeoutMs` / `AbortSignal` 组合）。
