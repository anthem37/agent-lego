# 工作流（前端）

- `types.ts`：`WorkflowDto`、`RunWorkflowResponse` 等。
- `api.ts`：`createWorkflow`、`getWorkflow`、`runWorkflow`。

## 与记忆策略（memoryNamespace）

后端执行工作流时，会将 definition JSON 中的可选字段 **`memoryNamespace`** 传入 `runAgent`（与 `POST /agents/{id}/run` 一致）：

- **单智能体**（definition 仅含 `agentId` + `modelId`）：可在根对象上设 `memoryNamespace`。
- **多步**（`steps[]`）：每个 step 对象可单独设 `memoryNamespace`。

详见仓库根目录 `docs/memory-strategy.md`。

## 创建页（表单生成 definition）

`/workflows` 创建页支持在**单智能体**模式下填写可选 **`记忆命名空间`**，以及在 **多步** 每一步填写可选 *
*`memoryNamespace`**，保存时会写入 JSON（空值不写入字段）。
