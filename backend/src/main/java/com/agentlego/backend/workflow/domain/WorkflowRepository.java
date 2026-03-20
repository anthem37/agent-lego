package com.agentlego.backend.workflow.domain;

import java.util.Optional;

public interface WorkflowRepository {
    String save(WorkflowAggregate aggregate);

    Optional<WorkflowAggregate> findById(String id);
}

