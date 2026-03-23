package com.agentlego.backend.memorypolicy.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

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

    /**
     * ASSISTANT_SUMMARY 时粗略摘要字符上限；空则运行时默认 480。
     */
    private Integer roughSummaryMaxChars;

    /**
     * VECTOR/HYBRID 时必填（与知识库集合一致）
     */
    private String vectorStoreProfileId;
    /**
     * 仅允许覆盖 collectionName
     */
    private Map<String, Object> vectorStoreConfig;
    private Double vectorMinScore;
}
