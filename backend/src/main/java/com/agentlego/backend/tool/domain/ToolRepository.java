package com.agentlego.backend.tool.domain;

import java.util.List;
import java.util.Optional;

public interface ToolRepository {
    String save(ToolAggregate aggregate);

    void update(ToolAggregate aggregate);

    int deleteById(String id);

    boolean existsByToolTypeAndName(ToolType toolType, String name);

    boolean existsByToolTypeAndNameExcludingId(ToolType toolType, String name, String excludeId);

    Optional<ToolAggregate> findById(String id);

    List<ToolAggregate> findAll();
}

