package com.agentlego.backend.agent.domain;

import java.util.Optional;

public interface AgentRepository {
    String save(AgentAggregate aggregate);

    Optional<AgentAggregate> findById(String id);
}

