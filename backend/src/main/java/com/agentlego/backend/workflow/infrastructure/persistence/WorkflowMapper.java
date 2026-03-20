package com.agentlego.backend.workflow.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkflowMapper {
    int insert(WorkflowDO workflow);

    WorkflowDO findById(@Param("id") String id);
}

