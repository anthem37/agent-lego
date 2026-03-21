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

import java.util.*;

/**
 * 平台知识库 → 检索 {@link Knowledge} 实现：pgvector 余弦近邻 + {@code content_tsv} 全文通道，
 * 应用层 RRF 融合后再按融合分与阈值截断。
 */
public final class KbVectorKnowledge implements Knowledge {

    private final List<String> collectionIds;
    private final String embeddingModelId;
    private final KbChunkRepository chunkRepository;
    private final ModelEmbeddingClient embeddingClient;
    private final int candidateLimit;
    /**
     * false 时仅走向量近邻，不调用 {@link KbChunkRepository#searchByFullText}。
     */
    private final boolean fullTextEnabled;

    public KbVectorKnowledge(
            List<String> collectionIds,
            String embeddingModelId,
            KbChunkRepository chunkRepository,
            ModelEmbeddingClient embeddingClient,
            int candidateLimit,
            boolean fullTextEnabled
    ) {
        this.collectionIds = List.copyOf(Objects.requireNonNull(collectionIds));
        this.embeddingModelId = Objects.requireNonNull(embeddingModelId);
        this.chunkRepository = Objects.requireNonNull(chunkRepository);
        this.embeddingClient = Objects.requireNonNull(embeddingClient);
        this.candidateLimit = Math.max(50, candidateLimit);
        this.fullTextEnabled = fullTextEnabled;
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
        Integer lim = config != null ? config.getLimit() : null;
        int topK = lim != null ? Math.max(1, lim) : 5;
        Double cfgTh = config != null ? config.getScoreThreshold() : null;
        double threshold = cfgTh != null ? cfgTh : 0.25d;

        List<float[]> qv = embeddingClient.embed(embeddingModelId, List.of(q));
        if (qv.isEmpty()) {
            return List.of();
        }
        float[] queryVec = ModelEmbeddingDimensions.padForPgStorage(qv.get(0));

        List<KbChunkHit> vecHits = chunkRepository.searchByCosineSimilarity(collectionIds, queryVec, candidateLimit);
        // 单字中文等短查询也走全文通道，避免仅靠向量漏召回专有名词（可通过策略或配置关闭）
        List<KbChunkHit> kwHits = fullTextEnabled
                ? chunkRepository.searchByFullText(collectionIds, q, candidateLimit)
                : List.of();

        Map<String, Double> vecSimById = new HashMap<>();
        for (KbChunkHit h : vecHits) {
            if (h.getSimilarity() != null) {
                vecSimById.put(h.getId(), h.getSimilarity());
            }
        }
        Map<String, Double> kwSimById = new HashMap<>();
        for (KbChunkHit h : kwHits) {
            if (h.getSimilarity() != null) {
                kwSimById.put(h.getId(), h.getSimilarity());
            }
        }

        Map<String, Double> rrf = new HashMap<>();
        int rrfK = 60;
        for (int i = 0; i < vecHits.size(); i++) {
            rrf.merge(vecHits.get(i).getId(), 1.0 / (rrfK + i + 1), Double::sum);
        }
        for (int i = 0; i < kwHits.size(); i++) {
            rrf.merge(kwHits.get(i).getId(), 1.0 / (rrfK + i + 1), Double::sum);
        }
        if (rrf.isEmpty()) {
            return List.of();
        }

        Map<String, KbChunkHit> hitById = new LinkedHashMap<>();
        for (KbChunkHit h : vecHits) {
            hitById.putIfAbsent(h.getId(), h);
        }
        for (KbChunkHit h : kwHits) {
            hitById.putIfAbsent(h.getId(), h);
        }

        List<String> orderedIds = rrf.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .toList();

        List<Document> out = new ArrayList<>();
        for (String id : orderedIds) {
            double vec = vecSimById.getOrDefault(id, 0.0);
            double kwRaw = kwSimById.getOrDefault(id, 0.0);
            double kwNorm = Math.min(1.0, kwRaw * 10.0);
            double fused = Math.max(vec, kwNorm);
            if (fused < threshold) {
                continue;
            }
            KbChunkHit h = hitById.get(id);
            if (h == null) {
                continue;
            }
            DocumentMetadata meta = DocumentMetadata.builder()
                    .content(TextBlock.builder().text(h.getContent()).build())
                    .docId(h.getDocumentId())
                    .chunkId(h.getId())
                    .build();
            Document d = new Document(meta);
            d.setScore(fused);
            out.add(d);
            if (out.size() >= topK) {
                break;
            }
        }
        return out;
    }
}
