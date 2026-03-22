package com.agentlego.backend.kb.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbCollectionMapper {

    int insert(KbCollectionDO row);

    KbCollectionDO findById(@Param("id") String id);

    List<KbCollectionDO> listAll();

    List<KbCollectionDO> findByIds(@Param("ids") List<String> ids);

    int deleteById(@Param("id") String id);

    int countByVectorStoreProfileId(@Param("profileId") String profileId);

    List<KbCollectionDO> findByVectorStoreProfileId(@Param("profileId") String profileId);
}
