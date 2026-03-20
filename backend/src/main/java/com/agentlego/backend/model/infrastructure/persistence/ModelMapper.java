package com.agentlego.backend.model.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ModelMapper {
    int insert(ModelDO model);

    ModelDO findById(@Param("id") String id);
}

