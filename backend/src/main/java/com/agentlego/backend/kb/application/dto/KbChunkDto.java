package com.agentlego.backend.kb.application.dto;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class KbChunkDto {
    private String id;
    private String documentId;
    private int chunkIndex;
    private String content;
    private Map<String, Object> metadata;
    private Instant createdAt;
}

