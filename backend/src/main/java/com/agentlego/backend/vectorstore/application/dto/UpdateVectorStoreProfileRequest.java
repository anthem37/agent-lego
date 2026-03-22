package com.agentlego.backend.vectorstore.application.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateVectorStoreProfileRequest {
    @Size(max = 256)
    private String name;
    private String vectorStoreKind;
    private Map<String, Object> vectorStoreConfig;
    private String embeddingModelId;
}
