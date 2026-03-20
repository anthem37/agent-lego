package com.agentlego.backend.kb.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KbQueryRequest {
    @NotBlank
    private String kbKey;

    @NotBlank
    private String queryText;

    @Min(1)
    private int topK = 5;
}

