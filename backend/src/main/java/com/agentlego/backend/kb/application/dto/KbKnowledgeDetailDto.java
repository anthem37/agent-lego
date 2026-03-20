package com.agentlego.backend.kb.application.dto;

import lombok.Data;

import java.time.Instant;

/** 知识详情（含全文，供预览 / 后续编辑） */
@Data
public class KbKnowledgeDetailDto {
    private String id;
    private String baseId;
    private String kbKey;
    private String name;
    private String contentRich;
    /** markdown | html */
    private String contentFormat;
    private String chunkStrategy;
    private Instant createdAt;
}
