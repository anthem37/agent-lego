package com.agentlego.backend.kb.domain;

import lombok.Data;

import java.time.Instant;

@Data
public class KbDocumentRow {
    private String id;
    private String collectionId;
    private String title;
    private String body;
    private String status;
    private String errorMessage;
    /**
     * 关联工具 ID 列表 JSON（字符串数组）。
     */
    private String linkedToolIdsJson = "[]";
    /**
     * 占位符绑定 JSON。
     */
    private String toolOutputBindingsJson = "{\"mappings\":[]}";
    private Instant createdAt;
    private Instant updatedAt;
}
