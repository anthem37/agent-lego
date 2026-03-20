package com.agentlego.backend.workflow.application.dto;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class WorkflowDto {
    private String id;
    private String name;
    private Map<String, Object> definition;
    private Instant createdAt;
}

