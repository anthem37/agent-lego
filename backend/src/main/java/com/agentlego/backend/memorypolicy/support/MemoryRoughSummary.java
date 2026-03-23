package com.agentlego.backend.memorypolicy.support;

/**
 * 本地「粗略摘要」：不调用模型，按字数上限与简单句读边界截断，供 {@code ASSISTANT_SUMMARY} 落库。
 */
public final class MemoryRoughSummary {

    /**
     * 默认写入记忆条目的最大字符数（UTF-16 码元）
     */
    public static final int DEFAULT_MAX_CHARS = 480;

    private MemoryRoughSummary() {
    }

    /**
     * 与 {@link #summarize(String, int)} 使用 {@link #DEFAULT_MAX_CHARS}。
     */
    public static String summarize(String text) {
        return summarize(text, DEFAULT_MAX_CHARS);
    }

    /**
     * 将策略上的可选上限解析为实际传给 {@link #summarize(String, int)} 的值：null 或无效时用 {@link #DEFAULT_MAX_CHARS}，否则夹在 16～8192。
     */
    public static int resolveMaxChars(Integer policyRoughSummaryMaxChars) {
        if (policyRoughSummaryMaxChars == null) {
            return DEFAULT_MAX_CHARS;
        }
        int v = policyRoughSummaryMaxChars;
        if (v < 16) {
            return 16;
        }
        if (v > 8192) {
            return 8192;
        }
        return v;
    }

    /**
     * 空白规整；短于上限则原样返回；否则优先在句号、换行等处截断并加省略号。
     */
    public static String summarize(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String t = text.replace("\r\n", "\n").trim();
        if (t.isEmpty()) {
            return "";
        }
        if (maxChars < 16) {
            maxChars = 16;
        }
        if (t.length() <= maxChars) {
            return t;
        }
        String chunk = t.substring(0, maxChars);
        int cut = lastRoughBoundary(chunk);
        if (cut > maxChars / 4) {
            return chunk.substring(0, cut).trim() + "…";
        }
        return chunk.trim() + "…";
    }

    /**
     * @return 截断位置（不包含该下标之后），找不到则 -1
     */
    static int lastRoughBoundary(String s) {
        for (int i = s.length() - 1; i >= Math.max(1, s.length() / 4); i--) {
            char c = s.charAt(i);
            if (c == '\n') {
                return i;
            }
            if (".。！？!?；;".indexOf(c) >= 0) {
                return i + 1;
            }
        }
        return -1;
    }
}
