package com.agentlego.backend.kb.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateKbBaseRequest {
    @NotBlank
    private String name;

    private String description;
}
