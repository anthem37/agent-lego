package com.agentlego.backend.tool.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ToolMapper {
    int insert(ToolDO tool);

    ToolDO findById(@Param("id") String id);

    List<ToolDO> findAll();
}

