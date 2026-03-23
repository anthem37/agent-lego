package com.agentlego.backend.tool.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class UpdateLocalBuiltinExposureRequest {

    @NotEmpty
    @Valid
    private List<LocalBuiltinExposureItemDto> items;
}
