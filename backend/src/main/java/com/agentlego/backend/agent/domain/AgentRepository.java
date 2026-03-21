package com.agentlego.backend.agent.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AgentRepository {
    String save(AgentAggregate aggregate);

    Optional<AgentAggregate> findById(String id);

    /**
     * 统计绑定到指定模型 ID 的智能体数量。
     */
    int countByModelId(String modelId);

    /**
     * 统计引用了指定工具记录 id（platform_tools.id）的智能体数量（经 platform_agent_tools）。
     */
    int countByToolId(String toolId);

    /**
     * 查询引用指定工具 id 的智能体 id 列表（上限由实现决定）。
     */
    List<String> listAgentIdsByToolId(String toolId);

    /**
     * {@code knowledge_base_policy.collectionIds} 中包含指定知识库集合 id 的智能体。
     */
    List<String> listAgentIdsReferencingKbCollection(String collectionId);

    /**
     * 仅更新 {@code knowledge_base_policy} 列（用于知识库集合删除后的策略收敛）。
     */
    void updateKnowledgeBasePolicy(String agentId, Map<String, Object> knowledgeBasePolicy);
}

