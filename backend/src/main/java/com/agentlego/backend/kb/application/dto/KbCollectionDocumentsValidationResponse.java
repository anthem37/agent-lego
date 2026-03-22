package com.agentlego.backend.kb.application.dto;

import lombok.Data;

import java.util.List;

@Data
public class KbCollectionDocumentsValidationResponse {
    private String collectionId;
    private String collectionName;
    private int totalDocuments;
    private int documentsOk;
    private int documentsWithErrors;
    private int documentsWithWarningsOnly;
    private List<KbCollectionDocumentValidationItemDto> items;
}
