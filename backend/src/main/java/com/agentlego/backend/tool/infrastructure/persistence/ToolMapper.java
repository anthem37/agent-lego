package com.agentlego.backend.tool.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ToolMapper {
    int insert(ToolDO tool);

    int update(ToolDO tool);

    int deleteById(@Param("id") String id);

    /**
     * 是否存在其它记录占用该名称（大小写不敏感）；{@code excludeId} 非空时排除该 id（用于更新）。
     */
    int countByNameIgnoreCaseExcluding(@Param("name") String name, @Param("excludeId") String excludeId);

    ToolDO findById(@Param("id") String id);

    List<ToolDO> findByIds(@Param("ids") List<String> ids);

    List<ToolDO> findAll();

    long countByQuery(@Param("q") String q, @Param("toolType") String toolType);

    List<ToolDO> findPageByQuery(
            @Param("q") String q,
            @Param("toolType") String toolType,
            @Param("offset") long offset,
            @Param("limit") int limit
    );
}

