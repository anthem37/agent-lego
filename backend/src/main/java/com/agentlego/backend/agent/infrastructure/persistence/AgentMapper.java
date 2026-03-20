package com.agentlego.backend.agent.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentMapper {
    int insert(AgentDO agent);

    AgentDO findById(@Param("id") String id);
}

