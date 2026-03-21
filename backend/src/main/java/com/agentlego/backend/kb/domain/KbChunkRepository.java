package com.agentlego.backend.kb.domain;

import java.util.List;

public interface KbChunkRepository {

    void insert(String id, String documentId, String collectionId, int chunkIndex, String content, float[] embeddingForStorage);

    /**
     * 使用 pgvector 余弦距离排序，返回至多 {@code limit} 条候选，再在应用层按阈值与 topK 截断。
     */
    List<KbChunkHit> searchByCosineSimilarity(
            List<String> collectionIds,
            float[] queryEmbeddingPadded,
            int limit
    );
}
