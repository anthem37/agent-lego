package com.agentlego.backend.kb.domain;

import java.util.Locale;

/**
 * 知识库集合绑定的外置向量存储类型。
 */
public enum KbVectorStoreKind {
    MILVUS,
    QDRANT;

    public static KbVectorStoreKind fromApi(String raw) {
        if (raw == null || raw.isBlank()) {
            return MILVUS;
        }
        return KbVectorStoreKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
