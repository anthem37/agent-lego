package com.agentlego.backend.kb.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

@Data
public class KbBaseDO {
    private String id;
    private String kbKey;
    private String name;
    private String description;
    private Instant createdAt;
    /**
     * 列表统计
     */
    private Long documentCount;
    private Instant lastIngestAt;
}
