package com.agentlego.backend.a2a.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class A2ADelegateRequest {
    @NotBlank
    private String agentId;

    @NotBlank
    private String modelId;

    @NotBlank
    private String input;

    /**
     * 可选：与 {@code RunAgentRequest#memoryNamespace} 一致。
     */
    private String memoryNamespace;
}

