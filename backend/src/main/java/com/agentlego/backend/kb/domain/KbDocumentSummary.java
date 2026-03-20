package com.agentlego.backend.kb.domain;

import lombok.Data;

import java.time.Instant;

/**
 * 知识（文档）列表项：隶属于某一知识库 baseId，并携带 kbKey 便于对照智能体配置。
 */
@Data
public class KbDocumentSummary {
    private String id;
    private String baseId;
    private String kbKey;
    private String name;
    /** markdown | html */
    private String contentFormat;
    /** 入库时使用的分片策略 */
    private String chunkStrategy;
    private int chunkCount;
    private Instant createdAt;
}
