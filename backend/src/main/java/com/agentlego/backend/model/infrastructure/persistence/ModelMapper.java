package com.agentlego.backend.model.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ModelMapper {
    int insert(ModelDO model);

    ModelDO findById(@Param("id") String id);

    List<ModelDO> listAllOrderByCreatedAtDesc();

    int update(ModelDO model);

    int deleteById(@Param("id") String id);
}

