# Agent Lego 前端

Next.js App Router + Ant Design 6 + TypeScript。

## 页面结构约定

- **`AppLayout`**：侧栏分组导航、顶栏面包屑与主内容区（`app-main-inner` 限宽）。
- **`PageShell`**：单页纵向区块栈，统一间距（默认 `gap={20}`）。
- **`PageHeaderBlock`**：页头卡片；可传 **`icon`**、**`backHref`**（返回列表）、**`extra`**。
- **`SectionCard`**：分区卡片（列表、表单、详情块）；支持 **`loading`**。
- **`ErrorAlert`**：统一错误展示。
- **`DetailSection` / `SurfaceBox`**（`components/ui/`）：抽屉/详情分块、预览区描边；详见 [DESIGN.md](./DESIGN.md)。

新增页面请按上述顺序组合，避免再写裸 `Space` + 零散 `Card`。

## 视觉与令牌

全站 **CSS 变量**（`app/globals.css`）与 **Ant Design 主题**（`AntdConfigProvider`）需保持一致；面向大众用户：**灰底 `#f0f2f5`
**、主色 **`#1677ff`**、侧栏 **`#001529`**、圆角约 **8～12px**，交互与常见企业后台一致。新增模块请阅读 *
*[DESIGN.md](./DESIGN.md)**，避免各页手写零散样式导致割裂。

## 表单与配置（重要）

- **禁止**在面向用户的页面中使用「整段 JSON 文本框」作为必填配置方式（易错、难审、体验差）。
- 应使用：**表单项、下拉、开关、`Form.List` 动态行、键值对表格** 等；复杂结构用分步/折叠区块。
- 仅在**只读排错**或开发者导出场景可展示格式化 JSON（如复制），且不应作为编辑主路径。
- 通用辅助：`lib/form-kv-helpers.ts`（键值对 → 对象、点号嵌套等）。

## 命令

- `npm run dev` — 开发
- `npm run build` — 生产构建
- `npm run lint` — ESLint
