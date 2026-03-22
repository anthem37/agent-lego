package com.agentlego.backend.vectorstore.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VectorStoreEmbeddingProbeRequest {
    @NotBlank
    @Size(max = 8192)
    private String text;
}
