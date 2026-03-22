package com.agentlego.backend.memorypolicy.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateMemoryPolicyRequest {

    @NotBlank
    private String name;

    private String description;

    @NotBlank
    private String ownerScope;

    /**
     * 默认见 {@link com.agentlego.backend.memorypolicy.support.MemoryPolicySemantic}
     */
    private String strategyKind;
    private String scopeKind;
    private String retrievalMode;

    private Integer topK;

    private String writeMode;

    private String writeBackOnDuplicate;
}
