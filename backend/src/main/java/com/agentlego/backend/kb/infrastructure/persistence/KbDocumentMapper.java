package com.agentlego.backend.kb.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbDocumentMapper {

    int insert(KbDocumentDO doc);

    int deleteById(@Param("id") String id);

    long countByBaseId(@Param("baseId") String baseId);

    List<KbDocumentDO> listByBaseIdPaged(
            @Param("baseId") String baseId,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    KbDocumentDO findById(@Param("id") String id);
}
