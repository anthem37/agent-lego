package com.agentlego.backend.memory.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MemoryItemMapper {
    int insert(MemoryItemDO item);

    List<MemoryItemDO> search(
            @Param("ownerScope") String ownerScope,
            @Param("queryText") String queryText,
            @Param("topK") int topK
    );
}

