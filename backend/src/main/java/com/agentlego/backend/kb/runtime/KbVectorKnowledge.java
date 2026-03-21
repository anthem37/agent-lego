package com.agentlego.backend.kb.runtime;

import com.agentlego.backend.kb.domain.KbChunkHit;
import com.agentlego.backend.kb.domain.KbChunkRepository;
import com.agentlego.backend.model.support.ModelEmbeddingClient;
import com.agentlego.backend.model.support.ModelEmbeddingDimensions;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.model.RetrieveConfig;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 平台知识库 → 检索 {@link Knowledge} 实现：查询向量化后由 PostgreSQL pgvector 做余弦近邻排序。
 */
public final class KbVectorKnowledge implements Knowledge {

    private final List<String> collectionIds;
    private final String embeddingModelId;
    private final KbChunkRepository chunkRepository;
    private final ModelEmbeddingClient embeddingClient;
    private final int candidateLimit;

    public KbVectorKnowledge(
            List<String> collectionIds,
            String embeddingModelId,
            KbChunkRepository chunkRepository,
            ModelEmbeddingClient embeddingClient,
            int candidateLimit
    ) {
        this.collectionIds = List.copyOf(Objects.requireNonNull(collectionIds));
        this.embeddingModelId = Objects.requireNonNull(embeddingModelId);
        this.chunkRepository = Objects.requireNonNull(chunkRepository);
        this.embeddingClient = Objects.requireNonNull(embeddingClient);
        this.candidateLimit = Math.max(50, candidateLimit);
    }

    @Override
    public Mono<Void> addDocuments(List<Document> documents) {
        return Mono.empty();
    }

    @Override
    public Mono<List<Document>> retrieve(String query, RetrieveConfig config) {
        return Mono.fromCallable(() -> doRetrieve(query, config))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private List<Document> doRetrieve(String query, RetrieveConfig config) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty() || collectionIds.isEmpty()) {
            return List.of();
        }
        int topK = config != null ? Math.max(1, config.getLimit()) : 5;
        double threshold = config != null ? config.getScoreThreshold() : 0.25d;

        List<float[]> qv = embeddingClient.embed(embeddingModelId, List.of(q));
        if (qv.isEmpty()) {
            return List.of();
        }
        float[] queryVec = ModelEmbeddingDimensions.padForPgStorage(qv.get(0));

        List<KbChunkHit> hits = chunkRepository.searchByCosineSimilarity(collectionIds, queryVec, candidateLimit);
        List<Document> out = new ArrayList<>();
        for (KbChunkHit h : hits) {
            double sim = h.getSimilarity() == null ? -1d : h.getSimilarity();
            if (sim < threshold) {
                continue;
            }
            DocumentMetadata meta = DocumentMetadata.builder()
                    .content(TextBlock.builder().text(h.getContent()).build())
                    .docId(h.getDocumentId())
                    .chunkId(h.getId())
                    .build();
            Document d = new Document(meta);
            d.setScore(sim);
            out.add(d);
            if (out.size() >= topK) {
                break;
            }
        }
        return out;
    }
}
