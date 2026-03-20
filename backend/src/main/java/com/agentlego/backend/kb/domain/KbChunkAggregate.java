package com.agentlego.backend.kb.domain;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 知识库分片（chunk）聚合根（Aggregate Root）。
 * <p>
 * 说明：
 * - chunkIndex 表示在文档内的顺序；
 * - metadata 用于承载分片来源、页码等扩展信息（JSON object）。
 */
@Data
public class KbChunkAggregate {
    /**
     * 分片 ID（Snowflake 字符串）。
     */
    private String id;
    /**
     * 关联的文档 ID。
     */
    private String documentId;
    /**
     * 分片序号（从 0 开始）。
     */
    private int chunkIndex;
    /**
     * 分片内容（文本）。
     */
    private String content;
    /**
     * 分片元数据（JSON object）。
     */
    private Map<String, Object> metadata;
    /**
     * 创建时间。
     */
    private Instant createdAt;
}

