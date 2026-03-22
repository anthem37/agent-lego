package com.agentlego.backend.kb.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

@Data
public class KbCollectionDO {
    private String id;
    private String name;
    private String description;
    private String embeddingModelId;
    private Integer embeddingDims;
    private String vectorStoreKind;
    private String vectorStoreConfigJson;
    private String vectorStoreProfileId;
    private String chunkStrategy;
    private String chunkParamsJson;
    private Instant createdAt;
    private Instant updatedAt;
}
