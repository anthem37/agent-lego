package com.agentlego.backend.agent.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AgentRepository {
    String save(AgentAggregate aggregate);

    /**
     * 全量更新智能体主表并替换工具关联（先删后插）。
     */
    void update(AgentAggregate aggregate);

    Optional<AgentAggregate> findById(String id);

    /**
     * 统计绑定到指定模型 ID 的智能体数量。
     */
    int countByModelId(String modelId);

    /**
     * 统计引用了指定工具记录 id（lego_tools.id）的智能体数量（经 lego_agent_tools）。
     */
    int countByToolId(String toolId);

    /**
     * 查询引用指定工具 id 的智能体 id 列表（上限由实现决定）。
     */
    List<String> listAgentIdsByToolId(String toolId);

    /**
     * 一次查询返回：引用该工具的智能体总数 + 样本 id（按创建时间倒序，条数有上限），供工具管理引用面板使用。
     */
    AgentToolReferenceSnapshot findToolReferencesByToolId(String toolId);

    /**
     * {@code knowledge_base_policy.collectionIds} 中包含指定知识库集合 id 的智能体。
     */
    List<String> listAgentIdsReferencingKbCollection(String collectionId);

    /**
     * 控制台知识库：列出智能体 id、名称与 knowledge_base_policy JSON（用于解析 collectionIds）。
     */
    List<AgentKbPolicyPickerRow> listKbPolicyPickerRows();

    /**
     * 仅更新 {@code knowledge_base_policy} 列（用于知识库集合删除后的策略收敛）。
     */
    void updateKnowledgeBasePolicy(String agentId, Map<String, Object> knowledgeBasePolicy);

    /**
     * 绑定到指定记忆策略的智能体数量。
     */
    int countByMemoryPolicyId(String policyId);

    /**
     * 批量统计各策略被智能体引用次数（未出现的策略视为 0）。
     */
    Map<String, Integer> countAgentsByMemoryPolicyIds(List<String> policyIds);

    /**
     * 列出引用指定记忆策略的智能体（id、名称）。
     */
    List<AgentMemoryPolicyRefRow> listAgentsByMemoryPolicyId(String policyId);
}

