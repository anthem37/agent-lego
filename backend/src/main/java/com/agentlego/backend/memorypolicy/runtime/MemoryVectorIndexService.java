package com.agentlego.backend.memorypolicy.runtime;

import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.kb.domain.KbCollectionAggregate;
import com.agentlego.backend.kb.rag.KbRagRankedChunk;
import com.agentlego.backend.kb.vector.KbVectorChunkRow;
import com.agentlego.backend.kb.vector.KbVectorStore;
import com.agentlego.backend.memorypolicy.domain.MemoryItemRepository;
import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryItemDO;
import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryPolicyDO;
import com.agentlego.backend.memorypolicy.support.MemoryPolicySemantic;
import com.agentlego.backend.model.support.ModelEmbeddingClient;
import com.agentlego.backend.vectorstore.domain.VectorStoreProfileAggregate;
import com.agentlego.backend.vectorstore.domain.VectorStoreProfileRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 记忆条目向量写入与向量检索（复用 {@link KbVectorStore} + {@link ModelEmbeddingClient}）。
 */
@Service
public class MemoryVectorIndexService {

    private final KbVectorStore kbVectorStore;
    private final ModelEmbeddingClient modelEmbeddingClient;
    private final VectorStoreProfileRepository vectorStoreProfileRepository;
    private final MemoryItemRepository memoryItemRepository;

    public MemoryVectorIndexService(
            KbVectorStore kbVectorStore,
            ModelEmbeddingClient modelEmbeddingClient,
            VectorStoreProfileRepository vectorStoreProfileRepository,
            MemoryItemRepository memoryItemRepository
    ) {
        this.kbVectorStore = kbVectorStore;
        this.modelEmbeddingClient = modelEmbeddingClient;
        this.vectorStoreProfileRepository = vectorStoreProfileRepository;
        this.memoryItemRepository = memoryItemRepository;
    }

    private static List<MemoryItemDO> filterNamespaceAndStrategy(
            List<MemoryItemDO> rows,
            String memoryNamespace,
            String strategyKind
    ) {
        return rows.stream().filter(row -> matchesNsAndStrategy(row, memoryNamespace, strategyKind)).collect(Collectors.toList());
    }

    static boolean matchesNsAndStrategy(MemoryItemDO row, String memoryNamespace, String strategyKind) {
        Map<String, Object> meta = JsonMaps.parseObject(row.getMetadataJson());
        if (strategyKind != null && !strategyKind.isBlank()) {
            Object sko = meta.get("strategyKind");
            String sk = sko == null ? null : String.valueOf(sko);
            if (sk != null && !sk.isBlank() && !strategyKind.equals(sk)) {
                return false;
            }
        }
        if (memoryNamespace != null && !memoryNamespace.isBlank()) {
            Object mno = meta.get("memoryNamespace");
            String mn = mno == null ? "" : String.valueOf(mno);
            return memoryNamespace.equals(mn);
        }
        Object mno = meta.get("memoryNamespace");
        String mn = mno == null ? "" : String.valueOf(mno);
        return mn.isBlank();
    }

    public Optional<KbCollectionAggregate> resolveAggregate(MemoryPolicyDO policy) {
        return resolveVectorContext(policy).map(VectorResolution::aggregate);
    }

    private Optional<VectorResolution> resolveVectorContext(MemoryPolicyDO policy) {
        if (policy == null || !MemoryPolicySemantic.isVectorLinkConfigured(policy)) {
            return Optional.empty();
        }
        VectorStoreProfileAggregate prof = vectorStoreProfileRepository.findById(policy.getVectorStoreProfileId().trim())
                .orElse(null);
        if (prof == null) {
            return Optional.empty();
        }
        KbCollectionAggregate agg = new KbCollectionAggregate();
        agg.setEmbeddingModelId(prof.getEmbeddingModelId());
        agg.setEmbeddingDims(prof.getEmbeddingDims());
        agg.setVectorStoreKind(prof.getVectorStoreKind());
        agg.setVectorStoreConfigJson(policy.getVectorStoreConfigJson());
        agg.setVectorStoreProfileId(policy.getVectorStoreProfileId());
        agg.setChunkStrategy("FIXED_WINDOW");
        agg.setChunkParamsJson("{\"maxChars\":900,\"overlap\":0}");
        return Optional.of(new VectorResolution(agg, prof));
    }

    public void indexMemoryItem(MemoryPolicyDO policy, MemoryItemDO item) {
        if (item == null || !MemoryPolicySemantic.isVectorRetrieval(policy.getRetrievalMode())) {
            return;
        }
        Optional<VectorResolution> ctx = resolveVectorContext(policy);
        if (ctx.isEmpty()) {
            return;
        }
        VectorResolution resolved = ctx.get();
        VectorStoreProfileAggregate prof = resolved.profile();
        KbCollectionAggregate col = resolved.aggregate();
        String content = item.getContent() == null ? "" : item.getContent();
        List<float[]> vecs = modelEmbeddingClient.embed(prof.getEmbeddingModelId(), List.of(content));
        if (vecs.isEmpty()) {
            return;
        }
        KbVectorChunkRow row = new KbVectorChunkRow(item.getId(), item.getId(), 0, content, vecs.get(0));
        kbVectorStore.upsertChunks(col, List.of(row));
    }

    public void deleteMemoryVectors(MemoryPolicyDO policy, String itemId) {
        if (policy == null || itemId == null || itemId.isBlank()) {
            return;
        }
        Optional<KbCollectionAggregate> col = resolveAggregate(policy);
        if (col.isEmpty()) {
            return;
        }
        kbVectorStore.deleteByDocumentId(col.get(), itemId.trim());
    }

    /**
     * 删除记忆策略前调用：遍历条目按 document_id 删除外置向量点，避免仅 PG 级联删后 Milvus/Qdrant 残留。
     *
     * @return 尝试删除的条目数（与向量库是否实际存在该 id 无关）
     */
    public int purgeAllVectorsForPolicy(MemoryPolicyDO policy) {
        if (policy == null) {
            return 0;
        }
        if (!MemoryPolicySemantic.isVectorRetrieval(policy.getRetrievalMode())) {
            return 0;
        }
        Optional<KbCollectionAggregate> col = resolveAggregate(policy);
        if (col.isEmpty()) {
            return 0;
        }
        List<MemoryItemDO> items = memoryItemRepository.listByPolicyId(policy.getId());
        int n = 0;
        for (MemoryItemDO row : items) {
            if (row.getId() != null && !row.getId().isBlank()) {
                kbVectorStore.deleteByDocumentId(col.get(), row.getId().trim());
                n++;
            }
        }
        return n;
    }

    /**
     * 向量检索：命中后按 PG 过滤命名空间与 strategyKind，再按向量分数排序截断。
     */
    public List<MemoryItemDO> searchByVector(
            MemoryPolicyDO policy,
            String queryText,
            int topK,
            String memoryNamespace,
            String strategyKind
    ) {
        Optional<VectorResolution> ctx = resolveVectorContext(policy);
        if (ctx.isEmpty()) {
            return List.of();
        }
        VectorResolution resolved = ctx.get();
        KbCollectionAggregate col = resolved.aggregate();
        VectorStoreProfileAggregate prof = resolved.profile();
        String q = queryText == null ? "" : queryText.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        List<float[]> qvecs = modelEmbeddingClient.embed(prof.getEmbeddingModelId(), List.of(q));
        if (qvecs.isEmpty()) {
            return List.of();
        }
        float[] qv = qvecs.get(0);
        double minScore = policy.getVectorMinScore() == null ? 0.15d : policy.getVectorMinScore();
        int fetch = Math.max(topK * 5, 32);
        List<KbRagRankedChunk> hits = kbVectorStore.search(col, qv, fetch, minScore);
        Map<String, Double> scoreById = new LinkedHashMap<>();
        for (KbRagRankedChunk h : hits) {
            String id = h.chunkId() != null && !h.chunkId().isBlank() ? h.chunkId().trim() : "";
            if (id.isBlank() && h.documentId() != null && !h.documentId().isBlank()) {
                id = h.documentId().trim();
            }
            if (id.isBlank()) {
                continue;
            }
            scoreById.putIfAbsent(id, h.score());
        }
        if (scoreById.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(scoreById.keySet());
        List<MemoryItemDO> rows = memoryItemRepository.findByIds(policy.getId(), ids);
        rows = filterNamespaceAndStrategy(rows, memoryNamespace, strategyKind);
        rows.sort(Comparator.comparingDouble(r -> -scoreById.getOrDefault(r.getId(), 0.0d)));
        if (rows.size() > topK) {
            return rows.subList(0, topK);
        }
        return rows;
    }

    /**
     * 一次解析向量 Profile + 合并后的集合配置，避免多处重复 {@link VectorStoreProfileRepository#findById}。
     */
    private record VectorResolution(KbCollectionAggregate aggregate, VectorStoreProfileAggregate profile) {
    }
}
