package com.agentlego.backend.kb.application.dto;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class KbCollectionDto {
    private String id;
    private String name;
    private String description;
    private String embeddingModelId;
    private int embeddingDims;
    /**
     * 分片策略：FIXED_WINDOW | PARAGRAPH | HEADING_SECTION
     */
    private String chunkStrategy;
    /**
     * 分片参数：maxChars、overlap；HEADING_SECTION 另有 headingLevel、leadMaxChars。
     */
    private Map<String, Object> chunkParams;
    private Instant createdAt;
    private Instant updatedAt;
}
