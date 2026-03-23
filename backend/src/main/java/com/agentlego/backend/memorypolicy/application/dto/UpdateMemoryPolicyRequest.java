package com.agentlego.backend.memorypolicy.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateMemoryPolicyRequest {

    @NotBlank
    private String name;

    private String description;

    @NotBlank
    private String ownerScope;

    private String strategyKind;
    private String scopeKind;
    private String retrievalMode;

    private Integer topK;

    private String writeMode;

    private String writeBackOnDuplicate;

    /**
     * ASSISTANT_SUMMARY 时粗略摘要字符上限；不传则保持原值（除非 {@link #clearRoughSummaryMaxChars} 为 true）。
     */
    private Integer roughSummaryMaxChars;

    /**
     * 为 true 时将库中上限清空为 null（运行时使用平台默认 480）；与 {@link #roughSummaryMaxChars} 同时出现时以此为准。
     */
    private Boolean clearRoughSummaryMaxChars;

    private String vectorStoreProfileId;
    private Map<String, Object> vectorStoreConfig;
    private Double vectorMinScore;
    /**
     * 为 true 时清空向量库绑定与配置
     */
    private Boolean clearVectorLink;
}
