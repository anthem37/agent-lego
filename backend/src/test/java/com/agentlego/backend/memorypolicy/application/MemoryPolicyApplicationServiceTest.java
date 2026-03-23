package com.agentlego.backend.memorypolicy.application;

import com.agentlego.backend.agent.domain.AgentRepository;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.memorypolicy.application.dto.CreateMemoryPolicyRequest;
import com.agentlego.backend.memorypolicy.application.dto.UpdateMemoryPolicyRequest;
import com.agentlego.backend.memorypolicy.application.service.MemoryPolicyApplicationService;
import com.agentlego.backend.memorypolicy.domain.MemoryItemRepository;
import com.agentlego.backend.memorypolicy.domain.MemoryPolicyRepository;
import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryItemDO;
import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryPolicyDO;
import com.agentlego.backend.memorypolicy.runtime.MemoryVectorIndexService;
import com.agentlego.backend.memorypolicy.support.MemoryPolicyVectorConfigService;
import com.agentlego.backend.memorypolicy.support.MemoryRoughSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoryPolicyApplicationServiceTest {

    @Mock
    private MemoryPolicyRepository memoryPolicyRepository;
    @Mock
    private MemoryItemRepository memoryItemRepository;
    @Mock
    private AgentRepository agentRepository;
    @Mock
    private MemoryPolicyVectorConfigService memoryPolicyVectorConfigService;
    @Mock
    private MemoryVectorIndexService memoryVectorIndexService;

    private static MemoryPolicyDO existingPolicy(String id) {
        MemoryPolicyDO row = new MemoryPolicyDO();
        row.setId(id);
        row.setName("N");
        row.setOwnerScope("scope-x");
        row.setStrategyKind("EPISODIC_DIALOGUE");
        row.setScopeKind("CUSTOM_NAMESPACE");
        row.setRetrievalMode("KEYWORD");
        row.setTopK(5);
        row.setWriteMode("OFF");
        row.setWriteBackOnDuplicate("skip");
        row.setRoughSummaryMaxChars(600);
        row.setCreatedAt(Instant.now());
        row.setUpdatedAt(Instant.now());
        return row;
    }

    private static UpdateMemoryPolicyRequest updateReq() {
        UpdateMemoryPolicyRequest req = new UpdateMemoryPolicyRequest();
        req.setName("N2");
        req.setOwnerScope("scope-x");
        return req;
    }

    private MemoryPolicyApplicationService service() {
        return new MemoryPolicyApplicationService(
                memoryPolicyRepository,
                memoryItemRepository,
                agentRepository,
                memoryPolicyVectorConfigService,
                memoryVectorIndexService
        );
    }

    @Test
    void updatePolicy_roughSummaryMaxChars_updatesClampedValue() {
        when(memoryPolicyRepository.findById("p1")).thenReturn(Optional.of(existingPolicy("p1")));
        when(memoryPolicyRepository.existsOtherByOwnerScope(eq("scope-x"), eq("p1"))).thenReturn(false);

        UpdateMemoryPolicyRequest req = updateReq();
        req.setRoughSummaryMaxChars(200);

        service().updatePolicy("p1", req);

        ArgumentCaptor<MemoryPolicyDO> cap = ArgumentCaptor.forClass(MemoryPolicyDO.class);
        verify(memoryPolicyRepository).update(cap.capture());
        assertEquals(200, cap.getValue().getRoughSummaryMaxChars().intValue());
    }

    @Test
    void updatePolicy_clearRoughSummaryMaxChars_setsNull() {
        when(memoryPolicyRepository.findById("p1")).thenReturn(Optional.of(existingPolicy("p1")));
        when(memoryPolicyRepository.existsOtherByOwnerScope(eq("scope-x"), eq("p1"))).thenReturn(false);

        UpdateMemoryPolicyRequest req = updateReq();
        req.setClearRoughSummaryMaxChars(true);

        service().updatePolicy("p1", req);

        ArgumentCaptor<MemoryPolicyDO> cap = ArgumentCaptor.forClass(MemoryPolicyDO.class);
        verify(memoryPolicyRepository).update(cap.capture());
        assertNull(cap.getValue().getRoughSummaryMaxChars());
    }

    @Test
    void updatePolicy_clearRoughSummaryMaxChars_winsOverRoughValue() {
        when(memoryPolicyRepository.findById("p1")).thenReturn(Optional.of(existingPolicy("p1")));
        when(memoryPolicyRepository.existsOtherByOwnerScope(eq("scope-x"), eq("p1"))).thenReturn(false);

        UpdateMemoryPolicyRequest req = updateReq();
        req.setClearRoughSummaryMaxChars(true);
        req.setRoughSummaryMaxChars(200);

        service().updatePolicy("p1", req);

        ArgumentCaptor<MemoryPolicyDO> cap = ArgumentCaptor.forClass(MemoryPolicyDO.class);
        verify(memoryPolicyRepository).update(cap.capture());
        assertNull(cap.getValue().getRoughSummaryMaxChars());
    }

    @Test
    void createPolicy_storesNullRoughSummaryMaxCharsWhenOmitted() {
        when(memoryPolicyRepository.existsOtherByOwnerScope(eq("ns1"), isNull())).thenReturn(false);

        CreateMemoryPolicyRequest req = new CreateMemoryPolicyRequest();
        req.setName("P");
        req.setOwnerScope("ns1");

        service().createPolicy(req);

        ArgumentCaptor<MemoryPolicyDO> cap = ArgumentCaptor.forClass(MemoryPolicyDO.class);
        verify(memoryPolicyRepository).save(cap.capture());
        assertNull(cap.getValue().getRoughSummaryMaxChars());
    }

    @Test
    void createPolicy_storesClampedRoughSummaryMaxChars() {
        when(memoryPolicyRepository.existsOtherByOwnerScope(eq("ns2"), isNull())).thenReturn(false);

        CreateMemoryPolicyRequest req = new CreateMemoryPolicyRequest();
        req.setName("P");
        req.setOwnerScope("ns2");
        req.setRoughSummaryMaxChars(300);

        service().createPolicy(req);

        ArgumentCaptor<MemoryPolicyDO> cap = ArgumentCaptor.forClass(MemoryPolicyDO.class);
        verify(memoryPolicyRepository).save(cap.capture());
        assertEquals(300, cap.getValue().getRoughSummaryMaxChars().intValue());
    }

    @Test
    void createPolicy_clampsTinyRoughSummaryMaxCharsToMinimum() {
        when(memoryPolicyRepository.existsOtherByOwnerScope(eq("ns3"), isNull())).thenReturn(false);

        CreateMemoryPolicyRequest req = new CreateMemoryPolicyRequest();
        req.setName("P");
        req.setOwnerScope("ns3");
        req.setRoughSummaryMaxChars(3);

        service().createPolicy(req);

        ArgumentCaptor<MemoryPolicyDO> cap = ArgumentCaptor.forClass(MemoryPolicyDO.class);
        verify(memoryPolicyRepository).save(cap.capture());
        assertEquals(16, cap.getValue().getRoughSummaryMaxChars().intValue());
        assertEquals(16, MemoryRoughSummary.resolveMaxChars(3));
    }

    @Test
    void deletePolicy_purgesVectorsBeforeRepositoryDelete() {
        MemoryPolicyDO pol = existingPolicy("p-del");
        when(memoryPolicyRepository.findById("p-del")).thenReturn(Optional.of(pol));
        when(agentRepository.countByMemoryPolicyId("p-del")).thenReturn(0);

        service().deletePolicy("p-del");

        InOrder order = inOrder(memoryVectorIndexService, memoryPolicyRepository);
        order.verify(memoryVectorIndexService).purgeAllVectorsForPolicy(pol);
        order.verify(memoryPolicyRepository).deleteById("p-del");
    }

    @Test
    void deletePolicy_whenAgentsBound_throwsAndSkipsPurgeAndDelete() {
        MemoryPolicyDO pol = existingPolicy("p-in-use");
        when(memoryPolicyRepository.findById("p-in-use")).thenReturn(Optional.of(pol));
        when(agentRepository.countByMemoryPolicyId("p-in-use")).thenReturn(2);

        ApiException ex = assertThrows(ApiException.class, () -> service().deletePolicy("p-in-use"));
        assertEquals("MEMORY_POLICY_IN_USE", ex.getCode());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());

        verifyNoInteractions(memoryVectorIndexService);
        verify(memoryPolicyRepository, never()).deleteById(any());
    }

    @Test
    void reindexVectors_keywordMode_throws() {
        MemoryPolicyDO pol = existingPolicy("p1");
        when(memoryPolicyRepository.findById("p1")).thenReturn(Optional.of(pol));

        ApiException ex = assertThrows(ApiException.class, () -> service().reindexVectors("p1"));
        assertEquals("INVALID_MEMORY_POLICY", ex.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());

        verifyNoInteractions(memoryVectorIndexService);
        verify(memoryItemRepository, never()).listByPolicyId(any());
    }

    @Test
    void reindexVectors_vectorLinkNotConfigured_throws() {
        MemoryPolicyDO pol = existingPolicy("p-vec");
        pol.setRetrievalMode("VECTOR");
        pol.setVectorStoreProfileId("prof-1");
        pol.setVectorStoreConfigJson("{}");
        when(memoryPolicyRepository.findById("p-vec")).thenReturn(Optional.of(pol));

        ApiException ex = assertThrows(ApiException.class, () -> service().reindexVectors("p-vec"));
        assertEquals("INVALID_MEMORY_POLICY", ex.getCode());
        assertTrue(ex.getMessage().contains("向量链路"));

        verifyNoInteractions(memoryVectorIndexService);
        verify(memoryItemRepository, never()).listByPolicyId(any());
    }

    @Test
    void reindexVectors_vectorConfigured_indexesAllItems() {
        MemoryPolicyDO pol = existingPolicy("p-ok");
        pol.setRetrievalMode("VECTOR");
        pol.setVectorStoreProfileId("prof-1");
        pol.setVectorStoreConfigJson("{\"collectionName\":\"mem_col\"}");
        when(memoryPolicyRepository.findById("p-ok")).thenReturn(Optional.of(pol));

        MemoryItemDO i1 = new MemoryItemDO();
        i1.setId("a");
        i1.setPolicyId("p-ok");
        i1.setContent("c1");
        MemoryItemDO i2 = new MemoryItemDO();
        i2.setId("b");
        i2.setPolicyId("p-ok");
        i2.setContent("c2");
        when(memoryItemRepository.listByPolicyId("p-ok")).thenReturn(List.of(i1, i2));

        var result = service().reindexVectors("p-ok");
        assertEquals(2, result.getIndexedCount());

        verify(memoryVectorIndexService).indexMemoryItem(eq(pol), eq(i1));
        verify(memoryVectorIndexService).indexMemoryItem(eq(pol), eq(i2));
        verify(memoryVectorIndexService, times(2)).indexMemoryItem(any(), any());
    }

    @Test
    void reindexVectors_hybridConfigured_indexesAllItems() {
        MemoryPolicyDO pol = existingPolicy("p-hyb");
        pol.setRetrievalMode("HYBRID");
        pol.setVectorStoreProfileId("prof-1");
        pol.setVectorStoreConfigJson("{\"collectionName\":\"mem_col\"}");
        when(memoryPolicyRepository.findById("p-hyb")).thenReturn(Optional.of(pol));

        MemoryItemDO i1 = new MemoryItemDO();
        i1.setId("x");
        i1.setPolicyId("p-hyb");
        i1.setContent("one");
        when(memoryItemRepository.listByPolicyId("p-hyb")).thenReturn(List.of(i1));

        var result = service().reindexVectors("p-hyb");
        assertEquals(1, result.getIndexedCount());
        verify(memoryVectorIndexService).indexMemoryItem(eq(pol), eq(i1));
    }
}
