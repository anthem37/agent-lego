package com.agentlego.backend.kb.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KbValidationIssueDto {
    /**
     * ERROR | WARN | INFO
     */
    private String severity;
    private String code;
    private String message;
}
