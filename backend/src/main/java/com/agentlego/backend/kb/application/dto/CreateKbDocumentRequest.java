package com.agentlego.backend.kb.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateKbDocumentRequest {
    @NotBlank
    private String kbKey;

    @NotBlank
    private String name;

    @NotBlank
    private String content;

    @Min(100)
    private int chunkSize = 800;

    @Min(0)
    private int overlap = 100;
}

