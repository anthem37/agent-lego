package com.agentlego.backend.tool.domain;

import java.util.List;
import java.util.Optional;

public interface ToolRepository {
    String save(ToolAggregate aggregate);

    Optional<ToolAggregate> findById(String id);

    List<ToolAggregate> findAll();
}

