package com.agentlego.backend.kb.support;

import java.util.ArrayList;
import java.util.List;

/**
 * 从向量库中召回的分片正文里解析「相似问」列表（与 {@link KbIngestEmbeddingInputs} 写入格式一致）。
 */
public final class KbChunkSimilarQueries {

    /**
     * 与 {@link KbIngestEmbeddingInputs#buildSimilarBlock} 中 header 一致
     */
    private static final String MARKER = "\n\n相似问:\n";

    private KbChunkSimilarQueries() {
    }

    /**
     * @param chunkText 分片全文（入库时拼在末尾的相似问块）
     * @return 非空行，按原文顺序
     */
    public static List<String> parseFromChunkText(String chunkText) {
        if (chunkText == null || chunkText.isBlank()) {
            return List.of();
        }
        int idx = chunkText.indexOf(MARKER);
        if (idx < 0) {
            return List.of();
        }
        String tail = chunkText.substring(idx + MARKER.length());
        List<String> out = new ArrayList<>();
        for (String line : tail.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}
