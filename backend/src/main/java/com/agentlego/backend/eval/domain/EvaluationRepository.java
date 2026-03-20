package com.agentlego.backend.eval.domain;

import java.util.Optional;

public interface EvaluationRepository {
    String save(EvaluationAggregate aggregate);

    Optional<EvaluationAggregate> findById(String id);
}

