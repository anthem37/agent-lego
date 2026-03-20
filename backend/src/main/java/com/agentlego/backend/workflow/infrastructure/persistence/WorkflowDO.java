package com.agentlego.backend.workflow.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

@Data
public class WorkflowDO {
    private String id;
    private String name;
    private String definitionJson;
    private Instant createdAt;
}

