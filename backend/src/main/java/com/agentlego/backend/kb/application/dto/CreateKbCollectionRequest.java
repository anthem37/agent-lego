package com.agentlego.backend.kb.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateKbCollectionRequest {
    @NotBlank
    @Size(max = 256)
    private String name;
    private String description;
    @NotBlank
    private String embeddingModelId;
}
