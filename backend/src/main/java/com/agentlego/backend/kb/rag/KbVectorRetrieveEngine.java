package com.agentlego.backend.kb.rag;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.kb.domain.KbCollectionAggregate;
import com.agentlego.backend.kb.vector.KbVectorStore;
import com.agentlego.backend.model.support.ModelEmbeddingClient;
import com.agentlego.backend.model.support.ModelEmbeddingDimensions;
import io.agentscope.core.rag.model.RetrieveConfig;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 跨多个知识库集合（各自 Milvus / Qdrant 物理 collection）的向量检索与融合排序；
 * 实现 {@link KbVectorRetrieve}。
 */
public final class KbVectorRetrieveEngine implements KbVectorRetrieve {

    private final List<KbCollectionAggregate> collections;
    private final String embeddingModelId;
    private final ModelEmbeddingClient embeddingClient;
    private final KbVectorStore vectorStore;
    private final int perCollectionTopK;

    public KbVectorRetrieveEngine(
            List<KbCollectionAggregate> collections,
            String embeddingModelId,
            ModelEmbeddingClient embeddingClient,
            KbVectorStore vectorStore,
            int perCollectionTopK
    ) {
        this.collections = List.copyOf(collections);
        this.embeddingModelId = Objects.requireNonNull(embeddingModelId, "embeddingModelId");
        this.embeddingClient = Objects.requireNonNull(embeddingClient, "embeddingClient");
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore");
        this.perCollectionTopK = Math.max(1, perCollectionTopK);
    }

    @Override
    public List<KbRagRankedChunk> search(String query, RetrieveConfig config) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int limit = config == null ? 5 : Math.max(1, config.getLimit());
        double th = config == null ? 0.25d : config.getScoreThreshold();
        if (collections.isEmpty()) {
            return List.of();
        }
        int expectedDim = collections.get(0).getEmbeddingDims();
        for (KbCollectionAggregate c : collections) {
            if (c.getEmbeddingDims() != expectedDim) {
                throw new ApiException(
                        "VALIDATION_ERROR",
                        "多集合召回要求各集合 embedding_dims 一致",
                        HttpStatus.BAD_REQUEST
                );
            }
        }
        List<float[]> emb = embeddingClient.embed(embeddingModelId, List.of(query.trim()));
        if (emb.isEmpty()) {
            return List.of();
        }
        float[] qv = ModelEmbeddingDimensions.fitToCollectionDim(emb.get(0), expectedDim);
        int fetchK = Math.min(Math.max(limit * 4, limit), perCollectionTopK);
        List<KbRagRankedChunk> merged = new ArrayList<>();
        for (KbCollectionAggregate col : collections) {
            merged.addAll(vectorStore.search(col, qv, fetchK, th));
        }
        merged.sort(Comparator.comparingDouble(KbRagRankedChunk::score).reversed());
        if (merged.size() <= limit) {
            return merged;
        }
        return new ArrayList<>(merged.subList(0, limit));
    }
}
