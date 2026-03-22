package com.agentlego.backend.kb.application.dto;

import lombok.Data;

import java.util.List;

@Data
public class KbCollectionDocumentValidationItemDto {
    private String documentId;
    private String title;
    private boolean ok;
    private int errorCount;
    private int warnCount;
    private int infoCount;
    /**
     * includeIssues=true 时有值；否则为 null
     */
    private List<KbValidationIssueDto> issues;
}
