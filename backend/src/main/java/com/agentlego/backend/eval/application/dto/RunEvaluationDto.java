package com.agentlego.backend.eval.application.dto;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class RunEvaluationDto {
    private String id;
    private String evaluationId;
    private String status;
    private Map<String, Object> input;
    private Map<String, Object> metrics;
    private Map<String, Object> trace;
    private String error;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant createdAt;
}

