package com.agentlego.backend.kb.application.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class KbDocumentDto {
    private String id;
    private String collectionId;
    private String title;
    private String status;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
}
