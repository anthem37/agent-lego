package com.agentlego.backend.memorypolicy.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class CreateMemoryItemRequest {

    @NotBlank
    private String content;

    private Map<String, Object> metadata;
}
