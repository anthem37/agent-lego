package com.agentlego.backend.kb.domain;

import java.util.List;

public interface KbChunkRepository {

    void insert(
            String id,
            String documentId,
            String collectionId,
            int chunkIndex,
            String content,
            String embeddingText,
            String metadataJson,
            float[] embeddingForStorage
    );

    /**
     * 使用 pgvector 余弦距离排序，返回至多 {@code limit} 条候选，再在应用层按阈值与 topK 截断。
     */
    List<KbChunkHit> searchByCosineSimilarity(
            List<String> collectionIds,
            float[] queryEmbeddingPadded,
            int limit
    );

    /**
     * 全文检索（content_tsv），similarity 为 {@code ts_rank_cd}，仅作排序与混合打分参考。
     */
    List<KbChunkHit> searchByFullText(List<String> collectionIds, String query, int limit);
}
