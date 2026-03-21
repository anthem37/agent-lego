package com.agentlego.backend.kb.rag;

import com.agentlego.backend.kb.domain.KbChunkHit;
import com.agentlego.backend.kb.domain.KbChunkRepository;
import com.agentlego.backend.model.support.ModelEmbeddingClient;
import com.agentlego.backend.model.support.ModelEmbeddingDimensions;
import io.agentscope.core.rag.model.RetrieveConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 纯检索：pgvector 余弦 + 可选全文，RRF 融合与阈值截断；不负责文档后处理或 {@link io.agentscope.core.rag.model.Document} 装配。
 */
public final class KbRagRetrieveEngine {

    private final List<String> collectionIds;
    private final String embeddingModelId;
    private final KbChunkRepository chunkRepository;
    private final ModelEmbeddingClient embeddingClient;
    private final int candidateLimit;
    private final boolean fullTextEnabled;

    public KbRagRetrieveEngine(
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

    public List<KbRagRankedChunk> search(String query, RetrieveConfig config) {
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

        List<KbRagRankedChunk> out = new ArrayList<>();
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
            String content = h.getContent() == null ? "" : h.getContent();
            String docId = h.getDocumentId() == null ? "" : h.getDocumentId().trim();
            out.add(new KbRagRankedChunk(h.getId(), docId, content, fused));
            if (out.size() >= topK) {
                break;
            }
        }
        return out;
    }
}
