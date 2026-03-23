# 前端交互与视觉约定

目标：**符合大众用户常见后台/中台产品习惯** — 层次清晰、主色友好、中文易读、交互可预期（与 Ant Design 生态认知一致）。

## 设计令牌（`app/globals.css`）

| 变量                                                                   | 用途                                 |
|----------------------------------------------------------------------|------------------------------------|
| `--app-primary` / `--app-primary-rgb`                                | 主色（**`#1677ff`**，Ant Design 默认品牌蓝） |
| `--app-primary-soft` / `--app-primary-softer` / `--app-primary-ring` | 浅底、描边                              |
| `--app-border` / `--app-border-strong`                               | 描边（浅灰系）                            |
| `--app-radius-sm` / `--app-radius-md` / `--app-radius-lg`            | 圆角（约 **8 / 10 / 12px**）            |
| `--app-shadow-sm` / `--app-shadow-md`                                | 卡片阴影（克制）                           |
| `--app-surface`                                                      | 卡片/面板背景（白）                         |
| `--app-text-muted`                                                   | 次级说明                               |
| `--foreground-muted`                                                 | 面包屑等弱文案                            |
| `--app-sider-surface`                                                | 侧栏（**`#001529`**，常见深蓝导航）           |

暗色模式在 `@media (prefers-color-scheme: dark)` 中做基础覆盖；默认以**浅色**为主。

**文案与字体**：正文与导航使用系统/无衬线字体；仅在代码、地址等场景使用 `app-font-mono`。

## Ant Design

- 全局配置见 `components/AntdConfigProvider.tsx`（主色 **`#1677ff`**、圆角 **8**、中文、语义色与 Ant Design 默认一致）。
- **不要**在同一页面混用「裸 `Card` 无样式」与「带 `SectionCard` 的卡片」：列表/仪表盘级内容优先 **`SectionCard`**。

## 复用组件

| 组件                | 路径                                | 何时使用                         |
|-------------------|-----------------------------------|------------------------------|
| `PageShell`       | `components/PageShell.tsx`        | 页面纵向栈，默认 `gap={20}`          |
| `PageHeaderBlock` | `components/PageHeaderBlock.tsx`  | 顶栏标题区（与工具/知识库列表一致）           |
| `SectionCard`     | `components/SectionCard.tsx`      | 主内容卡片（圆角/边框与令牌一致）            |
| `DetailSection`   | `components/ui/DetailSection.tsx` | **抽屉/详情**内分块（标题 + hint + 内容） |
| `SurfaceBox`      | `components/ui/SurfaceBox.tsx`    | 预览区、代码块外框（统一描边与内边距）          |

知识库列表壳层样式见 `components/kb/kb-shell.module.css`，与 `DetailSection` 同属「白底 + 细边框」家族。

## 禁止/减少

- 避免新增 `border: 1px solid rgba(0,0,0,0.06)`，改用 **`SurfaceBox`** 或 `var(--app-border)`。
- 次级文字优先 `Typography.Text type="secondary"` 或 `color: var(--app-text-muted)`。

## 与后端契约

REST 统一 `ApiResponse` 包装；DTO 字段命名与前端 `frontend/lib/*/types.ts` 对齐（camelCase）。详见仓库
`docs/api-conventions.md`。
