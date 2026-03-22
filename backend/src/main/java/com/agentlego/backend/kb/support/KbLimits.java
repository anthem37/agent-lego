package com.agentlego.backend.kb.support;

/**
 * 知识库领域体量与长度上限（与配置项 {@code agentlego.kb.ingest.*} 区分：此处为硬编码业务约束）。
 */
public final class KbLimits {

    public static final int MAX_COLLECTION_NAME_CHARS = 256;
    public static final int MAX_DOCUMENT_TITLE_CHARS = 512;
    /**
     * 控制台多集合召回调试上限，与典型智能体策略规模一致
     */
    public static final int MAX_MULTI_COLLECTION_RETRIEVE = 32;
    /**
     * 控制台召回命中正文预览截断
     */
    public static final int PREVIEW_MAX_CONTENT_CHARS = 8000;
    private static final int DEFAULT_TOP_K_CAP = 20;
    private static final int DEFAULT_TOP_K_FLOOR = 1;

    private KbLimits() {
    }

    /**
     * 将请求中的 topK 约束到控制台合理区间。
     */
    public static int clampPreviewTopK(Integer raw) {
        int v = raw != null ? raw : 5;
        return Math.max(DEFAULT_TOP_K_FLOOR, Math.min(v, DEFAULT_TOP_K_CAP));
    }
}
