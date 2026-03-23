package com.agentlego.backend.tool.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LocalBuiltinExposureMapper {

    List<LocalBuiltinExposureDO> selectAll();

    /**
     * @return 插入行数（0 表示已存在）
     */
    int insertIfAbsent(@Param("toolName") String toolName);

    int update(
            @Param("toolName") String toolName,
            @Param("exposeMcp") boolean exposeMcp,
            @Param("showInUi") boolean showInUi
    );
}
