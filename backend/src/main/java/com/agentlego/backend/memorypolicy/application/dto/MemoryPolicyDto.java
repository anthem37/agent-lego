package com.agentlego.backend.memorypolicy.application.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
public class MemoryPolicyDto {
    private String id;
    private String name;
    private String description;
    private String ownerScope;
    private String strategyKind;
    private String scopeKind;
    private String retrievalMode;
    private Integer topK;
    private String writeMode;
    private String writeBackOnDuplicate;
    /**
     * ASSISTANT_SUMMARY 时本地粗略摘要最大字符数；null 表示使用平台默认（480）。
     */
    private Integer roughSummaryMaxChars;
    /**
     * VECTOR/HYBRID 时引用公共向量库 profile
     */
    private String vectorStoreProfileId;
    /**
     * 合并后的外置向量库配置（通常含 collectionName）
     */
    private Map<String, Object> vectorStoreConfig;
    /**
     * 向量检索最小相似度阈值
     */
    private Double vectorMinScore;
    /**
     * VECTOR/HYBRID 且 profile + 物理集合已配置，可外置检索
     */
    private Boolean vectorLinkConfigured;
    /**
     * 绑定该策略的智能体数量（列表/详情聚合，非表内冗余列）。
     */
    private Integer referencingAgentCount;
    /**
     * 与当前实现能力相关的提示（如 VECTOR 降级、ASSISTANT_SUMMARY 未接摘要等）。
     */
    private List<String> implementationWarnings;
    private Instant createdAt;
    private Instant updatedAt;
}
