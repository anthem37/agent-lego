package com.agentlego.backend.kb.application.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class KbDocumentSummaryDto {
    private String id;
    private String baseId;
    /**
     * 所属知识库的绑定键（便于对照智能体配置）
     */
    private String kbKey;
    private String name;
    /**
     * markdown | html
     */
    private String contentFormat;
    /**
     * fixed | paragraph | hybrid | markdown_sections
     */
    private String chunkStrategy;
    private int chunkCount;
    private Instant createdAt;
}
