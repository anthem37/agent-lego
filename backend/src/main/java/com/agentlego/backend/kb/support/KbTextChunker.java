package com.agentlego.backend.kb.support;

import java.util.ArrayList;
import java.util.List;

/**
 * 纯文本分片：固定窗口 + 重叠，首版够用；后续可换语义分段。
 */
public final class KbTextChunker {

    public static final int DEFAULT_MAX_CHARS = 900;
    public static final int DEFAULT_OVERLAP = 120;

    private KbTextChunker() {
    }

    public static List<String> chunk(String text) {
        return chunk(text, DEFAULT_MAX_CHARS, DEFAULT_OVERLAP);
    }

    public static List<String> chunk(String text, int maxChars, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String t = text.trim();
        int max = Math.max(128, maxChars);
        int ov = Math.max(0, Math.min(overlap, max / 2));
        List<String> out = new ArrayList<>();
        int step = max - ov;
        if (step <= 0) {
            step = max;
        }
        for (int start = 0; start < t.length(); start += step) {
            int end = Math.min(t.length(), start + max);
            out.add(t.substring(start, end));
            if (end >= t.length()) {
                break;
            }
        }
        return out;
    }
}
