package com.agentlego.backend.kb.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

@Data
public class KbDocumentDO {
    private String id;
    private String baseId;
    /** 列表联表 kb_bases 填充，便于对照智能体 knowledgeBasePolicy.kbKey */
    private String kbKey;
    private String name;
    /** 全文：markdown 为 MD 源码，html 为富文本源码（列表查询不加载） */
    private String contentRich;
    /** markdown | html */
    private String contentFormat;
    /** fixed | paragraph | hybrid | markdown_sections */
    private String chunkStrategy;
    private Instant createdAt;
    private Integer chunkCount;
}
