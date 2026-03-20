package com.agentlego.backend.workflow.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkflowRunMapper {
    int insert(WorkflowRunDO run);

    void updateStatusRunning(@Param("runId") String runId);

    void updateStatusSucceeded(@Param("runId") String runId, @Param("outputJson") String outputJson);

    void updateStatusFailed(@Param("runId") String runId, @Param("error") String error);

    WorkflowRunDO findById(@Param("id") String id);
}

