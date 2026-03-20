package com.agentlego.backend.kb.application.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class KbBaseDto {
    private String id;
    /**
     * 智能体绑定键，全局唯一
     */
    private String kbKey;
    /**
     * 展示名称
     */
    private String name;
    private String description;
    private Instant createdAt;
    private long documentCount;
    /**
     * 下属知识文档最近创建时间
     */
    private Instant lastIngestAt;
}
