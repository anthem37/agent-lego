package com.agentlego.backend.model.domain;

import java.util.Optional;

public interface ModelRepository {
    String save(ModelAggregate aggregate);

    Optional<ModelAggregate> findById(String id);
}

