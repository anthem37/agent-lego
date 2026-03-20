package com.agentlego.backend.memory.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class CreateMemoryItemRequest {
    @NotBlank
    private String ownerScope;

    @NotNull
    private Map<String, Object> metadata;

    @NotNull
    private String content;
}

