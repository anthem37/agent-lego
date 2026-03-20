package com.agentlego.backend.workflow.domain;

import java.util.Map;

public interface WorkflowRunRepository {
    String createRun(String workflowId, Map<String, Object> input, String idempotencyKey);

    void markRunning(String runId);

    void markSucceeded(String runId, Map<String, Object> output);

    void markFailed(String runId, String error);

    WorkflowRunAggregate findById(String runId);
}

