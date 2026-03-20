package com.agentlego.backend.workflow.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

@Data
public class WorkflowRunDO {
    private String id;
    private String workflowId;
    private String status;
    private String idempotencyKey;
    private String inputJson;
    private String outputJson;
    private String error;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant createdAt;
}

