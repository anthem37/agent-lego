package com.agentlego.backend.kb.domain;

import lombok.Data;

import java.time.Instant;

/**
 * 知识库（空间）聚合信息：与「知识」文档相互独立配置与生命周期。
 */
@Data
public class KbBaseSummary {
    private String id;
    /** 智能体 knowledgeBasePolicy 中绑定的稳定键 */
    private String kbKey;
    /** 展示名称 */
    private String name;
    private String description;
    private Instant createdAt;
    private long documentCount;
    private Instant lastIngestAt;
}
