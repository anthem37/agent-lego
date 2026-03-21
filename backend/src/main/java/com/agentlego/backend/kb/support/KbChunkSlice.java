package com.agentlego.backend.kb.support;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单条分片：{@link #content} 用于 RAG 上下文；{@link #embeddingText} 用于生成向量（可与 content 不同）。
 */
public record KbChunkSlice(String content, String embeddingText, Map<String, Object> metadata) {

    public KbChunkSlice {
        if (content == null) {
            content = "";
        }
        if (embeddingText == null || embeddingText.isBlank()) {
            embeddingText = content;
        }
        if (metadata == null) {
            metadata = Map.of();
        } else {
            metadata = Map.copyOf(metadata);
        }
    }

    public static KbChunkSlice uniform(String text) {
        return new KbChunkSlice(text, text, Map.of());
    }

    public static KbChunkSlice uniform(String text, Map<String, Object> metadata) {
        return new KbChunkSlice(text, text, metadata);
    }

    public static Map<String, Object> meta(String key, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(key, value);
        return m;
    }
}
