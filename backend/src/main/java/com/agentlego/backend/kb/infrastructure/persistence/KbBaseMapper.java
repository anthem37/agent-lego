package com.agentlego.backend.kb.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbBaseMapper {

    int insert(KbBaseDO row);

    int updateById(KbBaseDO row);

    int deleteById(@Param("id") String id);

    KbBaseDO findById(@Param("id") String id);

    KbBaseDO findByKbKey(@Param("kbKey") String kbKey);

    List<KbBaseDO> listAllWithStats();
}
