package com.agentlego.backend.agent.domain;

import java.util.List;
import java.util.Optional;

public interface AgentRepository {
    String save(AgentAggregate aggregate);

    Optional<AgentAggregate> findById(String id);

    /**
     * 统计绑定到指定模型 ID 的智能体数量。
     */
    int countByModelId(String modelId);

    /**
     * 统计 tool_ids 中包含指定工具记录 id 的智能体数量。
     */
    int countByToolId(String toolId);

    /**
     * 查询引用指定工具 id 的智能体 id 列表（上限由实现决定）。
     */
    List<String> listAgentIdsByToolId(String toolId);
}

