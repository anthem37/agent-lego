package com.agentlego.backend.eval.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

@Data
public class EvaluationRunDO {
    private String id;
    private String evaluationId;
    private String status;
    private String inputJson;
    private String metricsJson;
    private String traceJson;
    private String error;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant createdAt;
}

