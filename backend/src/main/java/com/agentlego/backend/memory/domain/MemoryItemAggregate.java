package com.agentlego.backend.memory.domain;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 记忆条目聚合根（Aggregate Root）。
 * <p>
 * 说明：
 * - ownerScope 用于区分“记忆归属范围”（例如 userId / agentId / sessionId 等，当前不做租户隔离）；
 * - metadata 以 JSON object 承载扩展信息，便于后续检索与过滤。
 */
@Data
public class MemoryItemAggregate {
    /**
     * 记忆条目 ID（Snowflake 字符串）。
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

