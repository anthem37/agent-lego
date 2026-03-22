package com.agentlego.backend.memorypolicy.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MemoryItemMapper {

    int insert(MemoryItemDO row);

    List<MemoryItemDO> searchByKeyword(
            @Param("policyId") String policyId,
            @Param("q") String q,
            @Param("limit") int limit
    );

    String findIdByPolicyIdAndContent(
            @Param("policyId") String policyId,
            @Param("content") String content
    );

    int touchUpdatedAt(@Param("id") String id);

    int deleteByPolicyIdAndItemId(@Param("policyId") String policyId, @Param("itemId") String itemId);
}
