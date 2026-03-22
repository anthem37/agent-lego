package com.agentlego.backend.kb.vector;

import com.agentlego.backend.kb.domain.KbCollectionAggregate;
import com.agentlego.backend.kb.rag.KbRagRankedChunk;

import java.util.List;

/**
 * 知识库外置向量存储：建表/集合、写入、按文档删除、检索、删物理集合。
 */
public interface KbVectorStore {

    void upsertChunks(KbCollectionAggregate col, List<KbVectorChunkRow> rows);

    void deleteByDocumentId(KbCollectionAggregate col, String documentId);

    void dropPhysicalCollection(KbCollectionAggregate col);

    List<KbRagRankedChunk> search(
            KbCollectionAggregate col,
            float[] queryVector,
            int topK,
            double minScore
    );
}
