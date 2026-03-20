package com.agentlego.backend.memory.application.dto;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 记忆条目 DTO。
 */
@Data
public class MemoryItemDto {
    /**
     * 记忆条目 ID。
     */
    private String id;
    /**
     * 归属范围（owner scope），例如 userId。
     */
    private String ownerScope;
    /**
     * 记忆内容（文本）。
     */
    private String content;
    /**
     * 元数据（JSON object）。
     */
    private Map<String, Object> metadata;
    /**
     * 创建时间。
     */
    private Instant createdAt;
}

