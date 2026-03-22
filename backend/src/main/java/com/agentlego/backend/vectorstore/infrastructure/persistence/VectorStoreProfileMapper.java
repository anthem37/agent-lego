package com.agentlego.backend.vectorstore.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VectorStoreProfileMapper {

    int insert(VectorStoreProfileDO row);

    VectorStoreProfileDO findById(@Param("id") String id);

    List<VectorStoreProfileDO> listAll();

    int update(VectorStoreProfileDO row);

    int deleteById(@Param("id") String id);
}
