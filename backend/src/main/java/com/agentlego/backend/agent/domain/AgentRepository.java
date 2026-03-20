package com.agentlego.backend.agent.domain;

import java.util.Optional;

public interface AgentRepository {
    String save(AgentAggregate aggregate);

    Optional<AgentAggregate> findById(String id);

    /**
     * 统计绑定到指定模型 ID 的智能体数量。
     */
    int countByModelId(String modelId);
}

