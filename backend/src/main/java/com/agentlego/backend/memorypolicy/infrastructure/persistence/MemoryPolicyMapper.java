package com.agentlego.backend.memorypolicy.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MemoryPolicyMapper {

    int insert(MemoryPolicyDO row);

    int update(MemoryPolicyDO row);

    MemoryPolicyDO findById(@Param("id") String id);

    List<MemoryPolicyDO> listAll();

    int deleteById(@Param("id") String id);

    int countByOwnerScopeExceptId(@Param("ownerScope") String ownerScope, @Param("exceptId") String exceptId);
}
