package com.agentlego.backend.kb.domain;

import java.util.Locale;

/**
 * 知识库文本分片策略（集合级配置，入库时生效）。
 */
public enum KbChunkStrategyKind {
    /**
     * 固定字符窗口 + 重叠（默认），适合通用长文。
     */
    FIXED_WINDOW,
    /**
     * 按空行分段后再按 maxChars 合并段落块，适合 Markdown/结构化说明。
     */
    PARAGRAPH,
    /**
     * 按 Markdown 一级/二级标题切节；向量用「路径 + 标题 + 引导段」，召回用整节正文。
     */
    HEADING_SECTION;

    public static KbChunkStrategyKind fromApi(String raw) {
        if (raw == null || raw.isBlank()) {
            return FIXED_WINDOW;
        }
        String n = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return valueOf(n);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "未知分片策略: " + raw + "；可选: FIXED_WINDOW, PARAGRAPH, HEADING_SECTION"
            );
        }
    }
}
