# 知识库（前端）

- `types.ts`：集合、文档、召回预览等 DTO。
- `api.ts`：全部 KB HTTP 封装；最后一参统一为可选 `KbFetchOpts`（`signal` / `timeoutMs`，与 `lib/api/request` 对齐）。
- `bootstrap.ts`：控制台首屏并行加载（集合 + 分片策略元数据 + 向量库 Profile）。
- `page-helpers.ts`：控制台纯函数（集合名校验正则、向量配置脱敏、时间展示、分片策略文案）。
- `document-table-columns.tsx`：文档列表表格列工厂 `buildKbDocumentTableColumns`（状态列用 `KbDocumentStatusTag`）。
- `retrieve-preview-table-columns.tsx`：召回预览命中表列工厂 `buildKbRetrievePreviewColumns`（单文档抽屉与多集合召回调试共用）。
- UI 组件（`components/kb/`）：
    - `KbDocumentStatusTag`、`KbSimilarQueriesHitCell`
    - `KbCreateCollectionModal`（新建集合表单）
    - `KbMultiCollectionRetrieveModal`（多集合召回调试）
    - `KbValidateCollectionModal`（整集合校验结果）
    - `KbViewDocumentDrawer`（文档详情：富文本/原文/校验/召回/渲染测试）
    - `KbCollectionListPanel`（左侧集合列表 + 搜索）、`KbDocumentsMainPanel`（右侧文档表与集合元数据折叠）
- 辅助：`kb-rich-*`、`tool-labels`、`render-test-field-options` 等。
