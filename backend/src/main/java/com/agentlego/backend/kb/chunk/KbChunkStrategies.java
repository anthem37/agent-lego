package com.agentlego.backend.kb.chunk;

/**
 * 知识分片策略（入库时按条选择，持久化在 kb_documents.chunk_strategy）。
 */
public final class KbChunkStrategies {

    /**
     * 固定字符滑窗 + overlap（历史默认）
     */
    public static final String FIXED = "fixed";
    /**
     * 按空行分段，小段合并，超长再滑窗
     */
    public static final String PARAGRAPH = "paragraph";
    /**
     * 在 paragraph 基础上，超长块优先在句号/换行等软边界截断
     */
    public static final String HYBRID = "hybrid";
    /**
     * 仅 Markdown：按 # 标题切节，每节转纯文本后再 paragraph+滑窗；非 MD 正文时回退为 hybrid
     */
    public static final String MARKDOWN_SECTIONS = "markdown_sections";

    private KbChunkStrategies() {
    }

    public static boolean isKnown(String s) {
        if (s == null) {
            return false;
        }
        return FIXED.equals(s)
                || PARAGRAPH.equals(s)
                || HYBRID.equals(s)
                || MARKDOWN_SECTIONS.equals(s);
    }
}
