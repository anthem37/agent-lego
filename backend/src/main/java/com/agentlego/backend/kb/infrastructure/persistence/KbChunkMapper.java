package com.agentlego.backend.kb.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbChunkMapper {

    int insert(
            @Param("id") String id,
            @Param("documentId") String documentId,
            @Param("collectionId") String collectionId,
            @Param("chunkIndex") int chunkIndex,
            @Param("content") String content,
            @Param("embeddingVecLiteral") String embeddingVecLiteral
    );

    List<KbChunkDO> searchByCosineSimilarity(
            @Param("collectionIds") List<String> collectionIds,
            @Param("queryVecLiteral") String queryVecLiteral,
            @Param("limit") int limit
    );
}
