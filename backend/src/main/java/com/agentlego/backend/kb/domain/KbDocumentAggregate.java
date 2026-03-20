package com.agentlego.backend.kb.domain;

import lombok.Data;

import java.time.Instant;

/**
 * 知识库文档聚合根（Aggregate Root）。
 * <p>
 * 说明：文档与 chunk 为 1:N 关系，chunk 用于检索与 RAG 注入。
 */
@Data
public class KbDocumentAggregate {
    /**
     * 文档 ID（Snowflake 字符串）。
     */
    private String id;
    /**
     * 知识库标识（kb key），用于分组隔离。
     */
    private String kbKey;
    /**
     * 文档名称。
     */
    private String name;
    /**
     * 创建时间。
     */
    private Instant createdAt;
}

