package com.agentlego.backend.kb.application.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class KbDocumentValidationResponse {
    /**
     * 无 ERROR 级别问题时为 true。
     */
    private boolean ok;
    private List<KbValidationIssueDto> issues = new ArrayList<>();
}
