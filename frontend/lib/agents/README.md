# 智能体模块（前端）

| 文件                  | 职责                                                                                                                                     |
|---------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| `types.ts`          | 与后端 Agent DTO 对齐；含 `RunAgentForm.memoryNamespace`、`RunAgentResponse.memory`（`implementationWarnings`、`roughSummaryMaxCharsResolved` 等） |
| `api.ts`            | `createAgent` / `updateAgent` / `getAgent` / `runAgent` HTTP 封装                                                                        |
| `build-policy.ts`   | 知识库策略、运行 options、**创建/更新请求体** `buildUpsertAgentRequestBody`                                                                            |
| `form-options.ts`   | 下拉选项构建与 `Select` 的 `filterOption` 共用                                                                                                   |
| `runtime-kinds.ts`  | REACT / CHAT 枚举与说明                                                                                                                     |
| `runtime-labels.ts` | 展示用文案（如运行时形态标题）                                                                                                                        |

关联：

- `hooks/useAgentFormRefs.ts`：创建/编辑页共用的模型、工具、知识库集合、记忆策略加载与派生选项。
- `components/agents/RuntimeKindPicker.tsx`：创建页运行时形态卡片选择器。
