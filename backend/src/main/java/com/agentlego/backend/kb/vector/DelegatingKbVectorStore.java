package com.agentlego.backend.kb.vector;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.kb.domain.KbCollectionAggregate;
import com.agentlego.backend.kb.domain.KbVectorStoreKind;
import com.agentlego.backend.kb.milvus.MilvusKnowledgeStore;
import com.agentlego.backend.kb.qdrant.QdrantVectorStore;
import com.agentlego.backend.kb.rag.KbRagRankedChunk;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 按集合 {@link KbVectorStoreKind} 路由到 Milvus 或 Qdrant。
 */
@Component
@Primary
public class DelegatingKbVectorStore implements KbVectorStore {

    private final MilvusKnowledgeStore milvus;
    private final QdrantVectorStore qdrant;

    public DelegatingKbVectorStore(MilvusKnowledgeStore milvus, QdrantVectorStore qdrant) {
        this.milvus = Objects.requireNonNull(milvus, "milvus");
        this.qdrant = Objects.requireNonNull(qdrant, "qdrant");
    }

    @Override
    public void upsertChunks(KbCollectionAggregate col, List<KbVectorChunkRow> rows) {
        delegate(col).upsertChunks(col, rows);
    }

    @Override
    public void deleteByDocumentId(KbCollectionAggregate col, String documentId) {
        delegate(col).deleteByDocumentId(col, documentId);
    }

    @Override
    public void dropPhysicalCollection(KbCollectionAggregate col) {
        delegate(col).dropPhysicalCollection(col);
    }

    @Override
    public List<KbRagRankedChunk> search(
            KbCollectionAggregate col,
            float[] queryVector,
            int topK,
            double minScore
    ) {
        return delegate(col).search(col, queryVector, topK, minScore);
    }

    private KbVectorStore delegate(KbCollectionAggregate col) {
        if (col == null) {
            throw new ApiException("VALIDATION_ERROR", "集合为空", HttpStatus.BAD_REQUEST);
        }
        KbVectorStoreKind kind = KbVectorStoreKind.fromApi(col.getVectorStoreKind());
        return switch (kind) {
            case MILVUS -> milvus;
            case QDRANT -> qdrant;
        };
    }
}
