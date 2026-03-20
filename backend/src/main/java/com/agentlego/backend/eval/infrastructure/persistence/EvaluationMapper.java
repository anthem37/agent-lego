package com.agentlego.backend.eval.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EvaluationMapper {
    int insert(EvaluationDO evaluation);

    EvaluationDO findById(@Param("id") String id);
}

