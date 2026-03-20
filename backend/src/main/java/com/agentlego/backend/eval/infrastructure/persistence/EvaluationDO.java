package com.agentlego.backend.eval.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

@Data
public class EvaluationDO {
    private String id;
    private String agentId;
    private String name;
    private String configJson;
    private Instant createdAt;
}

