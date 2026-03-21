package com.agentlego.backend.kb.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class IngestKbDocumentRequest {
    @NotBlank
    @Size(max = 512)
    private String title;
    @NotBlank
    @Size(max = 524288)
    private String body;
}
