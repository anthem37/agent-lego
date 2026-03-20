package com.agentlego.backend.kb.domain;

import lombok.Data;

import java.time.Instant;

/** 知识文档详情（含正文，列表接口不返回大字段） */
@Data
public class KbDocumentDetail {
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
