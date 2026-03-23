package com.agentlego.backend.memorypolicy.runtime;

import com.agentlego.backend.kb.rag.KbRagRankedChunk;
import com.agentlego.backend.kb.vector.KbVectorStore;
import com.agentlego.backend.memorypolicy.domain.MemoryItemRepository;
import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryItemDO;
import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryPolicyDO;
import com.agentlego.backend.model.support.ModelEmbeddingClient;
import com.agentlego.backend.vectorstore.domain.VectorStoreProfileAggregate;
import com.agentlego.backend.vectorstore.domain.VectorStoreProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoryVectorIndexServiceTest {

    @Mock
    private KbVectorStore kbVectorStore;
    @Mock
    private ModelEmbeddingClient modelEmbeddingClient;
    @Mock
    private VectorStoreProfileRepository vectorStoreProfileRepository;
    @Mock
    private MemoryItemRepository memoryItemRepository;

    private static MemoryPolicyDO vectorPolicy(String id) {
        MemoryPolicyDO p = new MemoryPolicyDO();
        p.setId(id);
        p.setRetrievalMode("VECTOR");
        p.setVectorStoreProfileId("vp1");
        p.setVectorStoreConfigJson("{\"collectionName\":\"mem_col\"}");
        return p;
    }

    private static VectorStoreProfileAggregate profile() {
        VectorStoreProfileAggregate prof = new VectorStoreProfileAggregate();
        prof.setId("vp1");
        prof.setEmbeddingModelId("em1");
        prof.setEmbeddingDims(1024);
        prof.setVectorStoreKind("MILVUS");
        return prof;
    }

    private MemoryVectorIndexService service() {
        return new MemoryVectorIndexService(
                kbVectorStore,
                modelEmbeddingClient,
                vectorStoreProfileRepository,
                memoryItemRepository
        );
    }

    @Test
    void purgeAllVectorsForPolicy_null_returnsZero() {
        assertEquals(0, service().purgeAllVectorsForPolicy(null));
        verifyNoInteractions(kbVectorStore);
    }

    @Test
    void purgeAllVectorsForPolicy_keywordMode_returnsZero() {
        MemoryPolicyDO p = new MemoryPolicyDO();
        p.setId("p1");
        p.setRetrievalMode("KEYWORD");
        assertEquals(0, service().purgeAllVectorsForPolicy(p));
        verifyNoInteractions(kbVectorStore);
        verify(memoryItemRepository, never()).listByPolicyId(any());
    }

    @Test
    void purgeAllVectorsForPolicy_vectorProfileMissing_returnsZero() {
        MemoryPolicyDO p = vectorPolicy("p1");
        when(vectorStoreProfileRepository.findById("vp1")).thenReturn(Optional.empty());

        assertEquals(0, service().purgeAllVectorsForPolicy(p));

        verify(kbVectorStore, never()).deleteByDocumentId(any(), any());
        verify(memoryItemRepository, never()).listByPolicyId(any());
    }

    @Test
    void purgeAllVectorsForPolicy_deletesByDocumentIdForEachItem() {
        MemoryPolicyDO p = vectorPolicy("pol1");
        when(vectorStoreProfileRepository.findById("vp1")).thenReturn(Optional.of(profile()));

        MemoryItemDO i1 = new MemoryItemDO();
        i1.setId("doc-a");
        MemoryItemDO i2 = new MemoryItemDO();
        i2.setId("doc-b");
        MemoryItemDO skip = new MemoryItemDO();
        skip.setId("");
        when(memoryItemRepository.listByPolicyId("pol1")).thenReturn(List.of(i1, i2, skip));

        int n = service().purgeAllVectorsForPolicy(p);
        assertEquals(2, n);

        verify(kbVectorStore).deleteByDocumentId(any(), eq("doc-a"));
        verify(kbVectorStore).deleteByDocumentId(any(), eq("doc-b"));
    }

    @Test
    void purgeAllVectorsForPolicy_hybridMode_sameAsVector() {
        MemoryPolicyDO p = vectorPolicy("pol-h");
        p.setRetrievalMode("HYBRID");
        when(vectorStoreProfileRepository.findById("vp1")).thenReturn(Optional.of(profile()));

        MemoryItemDO i1 = new MemoryItemDO();
        i1.setId("x1");
        when(memoryItemRepository.listByPolicyId("pol-h")).thenReturn(List.of(i1));

        assertEquals(1, service().purgeAllVectorsForPolicy(p));
        verify(kbVectorStore).deleteByDocumentId(any(), eq("x1"));
    }

    @Test
    void matchesNsAndStrategy_strategyMismatch_returnsFalse() {
        MemoryItemDO row = new MemoryItemDO();
        row.setMetadataJson("{\"strategyKind\":\"OTHER\"}");
        assertFalse(MemoryVectorIndexService.matchesNsAndStrategy(row, null, "EPISODIC_DIALOGUE"));
    }

    @Test
    void matchesNsAndStrategy_strategyMatch_returnsTrue() {
        MemoryItemDO row = new MemoryItemDO();
        row.setMetadataJson("{\"strategyKind\":\"EPISODIC_DIALOGUE\"}");
        assertTrue(MemoryVectorIndexService.matchesNsAndStrategy(row, null, "EPISODIC_DIALOGUE"));
    }

    @Test
    void matchesNsAndStrategy_metaWithoutStrategy_passesStrategyCheck() {
        MemoryItemDO row = new MemoryItemDO();
        row.setMetadataJson("{}");
        assertTrue(MemoryVectorIndexService.matchesNsAndStrategy(row, null, "EPISODIC_DIALOGUE"));
    }

    @Test
    void matchesNsAndStrategy_namespaceFilter_matchAndMismatch() {
        MemoryItemDO ok = new MemoryItemDO();
        ok.setMetadataJson("{\"memoryNamespace\":\"ns1\"}");
        MemoryItemDO bad = new MemoryItemDO();
        bad.setMetadataJson("{\"memoryNamespace\":\"ns2\"}");
        assertTrue(MemoryVectorIndexService.matchesNsAndStrategy(ok, "ns1", null));
        assertFalse(MemoryVectorIndexService.matchesNsAndStrategy(bad, "ns1", null));
    }

    @Test
    void matchesNsAndStrategy_globalNamespace_metaBlank_returnsTrue() {
        MemoryItemDO row = new MemoryItemDO();
        row.setMetadataJson("{}");
        assertTrue(MemoryVectorIndexService.matchesNsAndStrategy(row, null, null));
    }

    @Test
    void matchesNsAndStrategy_globalNamespace_metaHasNs_returnsFalse() {
        MemoryItemDO row = new MemoryItemDO();
        row.setMetadataJson("{\"memoryNamespace\":\"reserved\"}");
        assertFalse(MemoryVectorIndexService.matchesNsAndStrategy(row, null, null));
    }

    @Test
    void searchByVector_blankQuery_returnsEmptyWithoutEmbedOrSearch() {
        MemoryPolicyDO p = vectorPolicy("p1");
        when(vectorStoreProfileRepository.findById("vp1")).thenReturn(Optional.of(profile()));
        assertTrue(service().searchByVector(p, "  ", 5, null, null).isEmpty());
        verifyNoInteractions(modelEmbeddingClient);
        verify(kbVectorStore, never()).search(any(), any(), anyInt(), anyDouble());
    }

    @Test
    void searchByVector_noAggregate_returnsEmpty() {
        MemoryPolicyDO p = new MemoryPolicyDO();
        p.setId("p1");
        p.setRetrievalMode("VECTOR");
        p.setVectorStoreProfileId(null);
        assertTrue(service().searchByVector(p, "hello", 5, null, null).isEmpty());
        verifyNoInteractions(modelEmbeddingClient);
    }

    @Test
    void searchByVector_embedEmpty_returnsEmpty() {
        MemoryPolicyDO p = vectorPolicy("p1");
        when(vectorStoreProfileRepository.findById("vp1")).thenReturn(Optional.of(profile()));
        when(modelEmbeddingClient.embed(eq("em1"), anyList())).thenReturn(List.of());
        assertTrue(service().searchByVector(p, "hello", 5, null, null).isEmpty());
        verify(kbVectorStore, never()).search(any(), any(), anyInt(), anyDouble());
    }

    @Test
    void searchByVector_respectsTopK_andSortsByScore() {
        MemoryPolicyDO p = vectorPolicy("p1");
        when(vectorStoreProfileRepository.findById("vp1")).thenReturn(Optional.of(profile()));
        when(modelEmbeddingClient.embed(eq("em1"), anyList())).thenReturn(List.of(new float[]{1f}));
        List<KbRagRankedChunk> hits = List.of(
                new KbRagRankedChunk("b", null, "", 0.5),
                new KbRagRankedChunk("a", null, "", 0.9)
        );
        when(kbVectorStore.search(any(), any(), eq(32), eq(0.15d))).thenReturn(hits);

        MemoryItemDO rowA = new MemoryItemDO();
        rowA.setId("a");
        MemoryItemDO rowB = new MemoryItemDO();
        rowB.setId("b");
        when(memoryItemRepository.findByIds(eq("p1"), anyList())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<String> ids = inv.getArgument(1);
            List<MemoryItemDO> out = new ArrayList<>();
            for (String id : ids) {
                if ("a".equals(id)) {
                    out.add(rowA);
                }
                if ("b".equals(id)) {
                    out.add(rowB);
                }
            }
            return out;
        });

        List<MemoryItemDO> out = service().searchByVector(p, "query", 1, null, null);
        assertEquals(1, out.size());
        assertEquals("a", out.get(0).getId());
    }

    @Test
    void searchByVector_usesDocumentIdWhenChunkIdBlank() {
        MemoryPolicyDO p = vectorPolicy("p1");
        when(vectorStoreProfileRepository.findById("vp1")).thenReturn(Optional.of(profile()));
        when(modelEmbeddingClient.embed(eq("em1"), anyList())).thenReturn(List.of(new float[]{1f}));
        when(kbVectorStore.search(any(), any(), eq(32), eq(0.15d)))
                .thenReturn(List.of(new KbRagRankedChunk("", "doc-x", "", 0.8)));

        MemoryItemDO row = new MemoryItemDO();
        row.setId("doc-x");
        when(memoryItemRepository.findByIds(eq("p1"), anyList())).thenReturn(List.of(row));

        List<MemoryItemDO> out = service().searchByVector(p, "q", 5, null, null);
        assertEquals(1, out.size());
        assertEquals("doc-x", out.get(0).getId());
    }

    @Test
    void searchByVector_filtersOutStrategyMismatch() {
        MemoryPolicyDO p = vectorPolicy("p1");
        when(vectorStoreProfileRepository.findById("vp1")).thenReturn(Optional.of(profile()));
        when(modelEmbeddingClient.embed(eq("em1"), anyList())).thenReturn(List.of(new float[]{1f}));
        when(kbVectorStore.search(any(), any(), eq(32), eq(0.15d)))
                .thenReturn(List.of(new KbRagRankedChunk("x", null, "", 0.9)));

        MemoryItemDO row = new MemoryItemDO();
        row.setId("x");
        row.setMetadataJson("{\"strategyKind\":\"OTHER\"}");
        when(memoryItemRepository.findByIds(eq("p1"), anyList())).thenReturn(List.of(row));

        assertTrue(service().searchByVector(p, "q", 5, null, "EPISODIC_DIALOGUE").isEmpty());
    }

    @Test
    void searchByVector_usesCustomVectorMinScore() {
        MemoryPolicyDO p = vectorPolicy("p1");
        p.setVectorMinScore(0.42d);
        when(vectorStoreProfileRepository.findById("vp1")).thenReturn(Optional.of(profile()));
        when(modelEmbeddingClient.embed(eq("em1"), anyList())).thenReturn(List.of(new float[]{1f}));
        when(kbVectorStore.search(any(), any(), eq(32), eq(0.42d))).thenReturn(List.of());

        assertTrue(service().searchByVector(p, "q", 5, null, null).isEmpty());
    }
}
