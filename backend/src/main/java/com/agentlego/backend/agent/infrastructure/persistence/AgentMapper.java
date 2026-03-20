package com.agentlego.backend.agent.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentMapper {
    int insert(AgentDO agent);

    AgentDO findById(@Param("id") String id);

    /**
     * 统计引用指定模型 ID 的智能体数量（用于删除前冲突检测）。
     */
    int countByModelId(@Param("modelId") String modelId);
}

