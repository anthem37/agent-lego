package com.agentlego.backend.vectorstore.domain;

import lombok.Data;

import java.time.Instant;

/**
 * 外置向量库连接配置（Milvus / Qdrant），供知识库集合使用。
 */
@Data
public class VectorStoreProfileAggregate {
    private String id;
    private String name;
    private String vectorStoreKind;
    private String vectorStoreConfigJson;
    private String embeddingModelId;
    private int embeddingDims;
    private Instant createdAt;
    private Instant updatedAt;
}
