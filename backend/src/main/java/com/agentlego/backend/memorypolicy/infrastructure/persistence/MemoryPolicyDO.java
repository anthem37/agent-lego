package com.agentlego.backend.memorypolicy.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

@Data
public class MemoryPolicyDO {
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
     * ASSISTANT_SUMMARY 时本地粗略摘要最大字符数；null 表示使用 {@link com.agentlego.backend.memorypolicy.support.MemoryRoughSummary#DEFAULT_MAX_CHARS}。
     */
    private Integer roughSummaryMaxChars;
    /**
     * VECTOR/HYBRID 时引用公共向量库；与 {@code vectorStoreConfigJson} 合并。
     */
    private String vectorStoreProfileId;
    /**
     * 合并后的外置向量库配置 JSON（与知识库集合一致，通常含 collectionName）。
     */
    private String vectorStoreConfigJson;
    /**
     * 向量检索最小相似度阈值。
     */
    private Double vectorMinScore;
    private Instant createdAt;
    private Instant updatedAt;
}
