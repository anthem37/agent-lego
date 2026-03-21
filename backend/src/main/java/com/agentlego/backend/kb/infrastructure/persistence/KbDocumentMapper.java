package com.agentlego.backend.kb.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbDocumentMapper {

    int insertPending(KbDocumentDO row);

    int updateStatus(
            @Param("id") String id,
            @Param("status") String status,
            @Param("errorMessage") String errorMessage
    );

    KbDocumentDO findById(@Param("id") String id);

    List<KbDocumentDO> listByCollectionId(@Param("collectionId") String collectionId);

    int deleteById(@Param("id") String id);
}
