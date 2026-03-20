package com.agentlego.backend.kb.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbChunkMapper {
    int insertChunk(KbChunkDO chunk);

    /**
     * 向量检索：加载某知识库下所有 chunk 的 embedding（可能为 null）。
     */
    List<KbChunkDO> listChunksByBaseIdWithEmbedding(@org.apache.ibatis.annotations.Param("baseId") String baseId);

    /**
     * 向量检索：更新 chunk 的 embedding 与元数据（metadata_json）。
     */
    int updateChunkEmbedding(
            @org.apache.ibatis.annotations.Param("chunkId") String chunkId,
            @org.apache.ibatis.annotations.Param("embeddingJson") String embeddingJson,
            @org.apache.ibatis.annotations.Param("metadataJson") String metadataJson
    );

    List<KbChunkDO> searchChunks(
            @Param("baseId") String baseId,
            @Param("queryText") String queryText,
            @Param("topK") int topK
    );
}

