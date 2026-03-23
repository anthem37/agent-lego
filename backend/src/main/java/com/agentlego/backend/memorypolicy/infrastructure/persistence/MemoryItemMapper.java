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
            @Param("limit") int limit,
            @Param("memoryNamespace") String memoryNamespace,
            @Param("strategyKind") String strategyKind,
            @Param("orderByTrgm") Boolean orderByTrgm
    );

    List<MemoryItemDO> findByIds(@Param("policyId") String policyId, @Param("ids") List<String> ids);

    List<MemoryItemDO> listByPolicyId(@Param("policyId") String policyId);

    String findIdByPolicyIdAndContent(
            @Param("policyId") String policyId,
            @Param("content") String content,
            @Param("memoryNamespace") String memoryNamespace,
            @Param("strategyKind") String strategyKind
    );

    int touchUpdatedAt(@Param("id") String id);

    int deleteByPolicyIdAndItemId(@Param("policyId") String policyId, @Param("itemId") String itemId);
}
