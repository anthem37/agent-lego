package com.agentlego.backend.vectorstore.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

@Data
public class VectorStoreProfileDO {
    private String id;
    private String name;
    private String vectorStoreKind;
    private String vectorStoreConfigJson;
    private String embeddingModelId;
    private Integer embeddingDims;
    private Instant createdAt;
    private Instant updatedAt;
}
