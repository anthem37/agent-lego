package com.agentlego.backend.tool.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ToolMapper {
    int insert(ToolDO tool);

    int update(ToolDO tool);

    int deleteById(@Param("id") String id);

    int countByTypeAndName(@Param("toolType") String toolType, @Param("name") String name);

    int countByTypeAndNameExcludeId(
            @Param("toolType") String toolType,
            @Param("name") String name,
            @Param("excludeId") String excludeId
    );

    ToolDO findById(@Param("id") String id);

    List<ToolDO> findAll();

    long countByQuery(@Param("q") String q);

    List<ToolDO> findPageByQuery(
            @Param("q") String q,
            @Param("offset") long offset,
            @Param("limit") int limit
    );
}

