/**
 * 知识「正文」编辑区统一尺寸（Markdown 与富文本须一致）
 *
 * - 高度：**64×8 = 512px**，对齐常见 8px 网格，避免与站内其它间距（多基于 4/8）冲突。
 * - Markdown：`@uiw/react-md-editor` 的 `height` = 组件总高（含工具栏）。
 * - 富文本：Quill snow = 工具栏 + 编辑区；编辑区 min-height = 总高 − 工具栏。
 */
export const KB_KNOWLEDGE_EDITOR_TOTAL_PX = 512;
/** Quill snow 工具栏视觉高度（与 Quill 默认一致，用于对齐「总高 512」） */
export const KB_QUILL_TOOLBAR_APPROX_PX = 42;

export const kbQuillEditorInnerMinPx = (): number =>
    KB_KNOWLEDGE_EDITOR_TOTAL_PX - KB_QUILL_TOOLBAR_APPROX_PX;
