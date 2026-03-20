package com.agentlego.backend.kb.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KbDocumentMapper {
    int insert(KbDocumentDO doc);

    KbDocumentDO findByKbKey(@Param("kbKey") String kbKey);
}

