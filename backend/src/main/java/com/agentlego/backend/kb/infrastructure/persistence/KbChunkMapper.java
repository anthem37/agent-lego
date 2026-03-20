package com.agentlego.backend.kb.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbChunkMapper {
    int insertChunk(KbChunkDO chunk);

    List<KbChunkDO> searchChunks(
            @Param("kbKey") String kbKey,
            @Param("queryText") String queryText,
            @Param("topK") int topK
    );
}

