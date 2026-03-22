package com.agentlego.backend.agent.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AgentMapper {
    int insert(AgentDO agent);

    AgentDO findById(@Param("id") String id);

    /**
     * 统计引用指定模型 ID 的智能体数量（用于删除前冲突检测）。
     */
    int countByModelId(@Param("modelId") String modelId);

    int countByToolId(@Param("toolId") String toolId);

    List<String> listAgentIdsByToolId(@Param("toolId") String toolId);

    List<String> listAgentIdsReferencingKbCollection(@Param("collectionId") String collectionId);

    List<AgentDO> listKbPolicyPicker();

    int updateKnowledgeBasePolicy(
            @Param("id") String id,
            @Param("knowledgeBasePolicyJson") String knowledgeBasePolicyJson
    );

    int insertAgentTools(@Param("agentId") String agentId, @Param("toolIds") List<String> toolIds);

    int deleteAgentToolsByAgentId(@Param("agentId") String agentId);

    int updateAgent(AgentDO agent);

    int countByMemoryPolicyId(@Param("policyId") String policyId);

    List<AgentMemoryPolicyCountRow> countAgentsByMemoryPolicyIds(@Param("policyIds") List<String> policyIds);

    List<AgentDO> listAgentsByMemoryPolicyId(@Param("policyId") String policyId);
}

