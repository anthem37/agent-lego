package com.agentlego.backend.eval.domain;

import java.util.Map;
import java.util.Optional;

public interface EvaluationRunRepository {
    String createRun(String evaluationId, Map<String, Object> input);

    void markRunning(String runId);

    void markSucceeded(String runId, Map<String, Object> metrics, Map<String, Object> trace);

    void markFailed(String runId, String error);

    Optional<EvaluationRunAggregate> findById(String runId);
}

