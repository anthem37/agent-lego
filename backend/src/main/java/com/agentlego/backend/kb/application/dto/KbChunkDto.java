package com.agentlego.backend.kb.application.dto;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class KbChunkDto {
    private String id;
    private String documentId;
    /**
     * 所属文档名称（检索结果展示，便于 RAG 溯源）
     */
    private String documentName;
    private int chunkIndex;
    private String content;
    private Map<String, Object> metadata;
    private Instant createdAt;
}

