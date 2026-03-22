package com.agentlego.backend.vectorstore.application.dto;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class VectorStoreProfileDto {
    private String id;
    private String name;
    private String vectorStoreKind;
    private Map<String, Object> vectorStoreConfig;
    private String embeddingModelId;
    private int embeddingDims;
    private Instant createdAt;
    private Instant updatedAt;
}
