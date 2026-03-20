package com.agentlego.backend.eval.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EvaluationRunMapper {
    int insert(EvaluationRunDO run);

    void updateStatusRunning(@Param("runId") String runId);

    void updateStatusSucceeded(
            @Param("runId") String runId,
            @Param("metricsJson") String metricsJson,
            @Param("traceJson") String traceJson
    );

    void updateStatusFailed(@Param("runId") String runId, @Param("error") String error);

    EvaluationRunDO findById(@Param("id") String id);
}

