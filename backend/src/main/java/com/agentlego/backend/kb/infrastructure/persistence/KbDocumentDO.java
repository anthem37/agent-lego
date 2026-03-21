package com.agentlego.backend.kb.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

@Data
public class KbDocumentDO {
    private String id;
    private String collectionId;
    private String title;
    private String body;
    /** 可选：富文本 HTML；分块仍以 body（Markdown）为准 */
    private String bodyRich;
    private String status;
    private String errorMessage;
    private String linkedToolIdsJson;
    private String toolOutputBindingsJson;
    private Instant createdAt;
    private Instant updatedAt;
}
