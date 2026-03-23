package com.agentlego.backend.tool.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LocalBuiltinExposureItemDto {

    @NotBlank
    private String toolName;

    @NotNull
    private Boolean exposeMcp;

    @NotNull
    private Boolean showInUi;
}
