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
    private Instant createdAt;
    private Instant updatedAt;
}
