# 模型模块（前端）

- `types.ts`：`ModelSummary` / `ModelDetail` / `ModelDto` / `ProviderMeta` / `TestModelResponse` 等。
- `api.ts`：`listModels`、`getModel`、`createModel`、`updateModel`、`deleteModel`、`testModel`、`listModelsAsSelectRows` 等。

列表页离线 fallback 的 `FALLBACK_PROVIDERS` 仍保留在 `app/models/page.tsx`（与后端白名单同步维护）。
