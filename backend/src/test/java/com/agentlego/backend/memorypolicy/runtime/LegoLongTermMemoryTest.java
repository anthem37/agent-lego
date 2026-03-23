package com.agentlego.backend.memorypolicy.runtime;

import com.agentlego.backend.kb.domain.KbCollectionAggregate;
import com.agentlego.backend.memorypolicy.domain.MemoryItemRepository;
import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryItemDO;
import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryPolicyDO;
import com.agentlego.backend.memorypolicy.support.MemoryPolicySemantic;
import io.agentscope.core.message.Msg;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegoLongTermMemoryTest {

    @Mock
    private MemoryItemRepository memoryItemRepository;
    @Mock
    private MemoryVectorIndexService memoryVectorIndexService;

    private static MemoryPolicyDO basePolicy() {
        MemoryPolicyDO p = new MemoryPolicyDO();
        p.setId("pol-1");
        p.setOwnerScope("owner-scope");
        p.setStrategyKind("EPISODIC_DIALOGUE");
        p.setTopK(5);
        p.setRetrievalMode(MemoryPolicySemantic.RETRIEVAL_KEYWORD);
        p.setWriteMode(MemoryPolicySemantic.WRITE_OFF);
        return p;
    }

    private static Msg msg(String text) {
        Msg m = mock(Msg.class);
        when(m.getTextContent()).thenReturn(text);
        return m;
    }

    private LegoLongTermMemory memory(MemoryPolicyDO policy) {
        return new LegoLongTermMemory(
                memoryItemRepository,
                policy,
                memoryVectorIndexService,
                "agent-1",
                null,
                480
        );
    }

    @Test
    void retrieve_keywordMode_callsSearchByKeyword() {
        MemoryPolicyDO p = basePolicy();
        when(memoryVectorIndexService.resolveAggregate(p)).thenReturn(Optional.empty());
        when(memoryItemRepository.searchByKeyword(eq("pol-1"), eq("hello"), eq(5), isNull(), anyString(), eq(true)))
                .thenReturn(List.of());

        memory(p).retrieve(msg("hello")).block();

        verify(memoryVectorIndexService).resolveAggregate(p);
        verify(memoryItemRepository).searchByKeyword(
                eq("pol-1"), eq("hello"), eq(5), isNull(), eq("EPISODIC_DIALOGUE"), eq(true)
        );
        verify(memoryVectorIndexService, never()).searchByVector(any(), anyString(), anyInt(), any(), any());
    }

    @Test
    void retrieve_vectorMode_notConfigured_fallsBackToKeyword() {
        MemoryPolicyDO p = basePolicy();
        p.setRetrievalMode(MemoryPolicySemantic.RETRIEVAL_VECTOR);
        when(memoryVectorIndexService.resolveAggregate(p)).thenReturn(Optional.empty());
        when(memoryItemRepository.searchByKeyword(any(), anyString(), anyInt(), isNull(), anyString(), anyBoolean()))
                .thenReturn(List.of());

        memory(p).retrieve(msg("q")).block();

        verify(memoryVectorIndexService).resolveAggregate(p);
        verify(memoryItemRepository).searchByKeyword(
                eq("pol-1"), eq("q"), eq(5), isNull(), eq("EPISODIC_DIALOGUE"), eq(true)
        );
        verify(memoryVectorIndexService, never()).searchByVector(any(), anyString(), anyInt(), any(), any());
    }

    @Test
    void retrieve_vectorMode_configured_usesSearchByVector() {
        MemoryPolicyDO p = basePolicy();
        p.setRetrievalMode(MemoryPolicySemantic.RETRIEVAL_VECTOR);
        when(memoryVectorIndexService.resolveAggregate(p)).thenReturn(Optional.of(new KbCollectionAggregate()));

        MemoryItemDO row = new MemoryItemDO();
        row.setId("i1");
        row.setContent("from-vector");
        when(memoryVectorIndexService.searchByVector(eq(p), eq("q"), eq(5), isNull(), eq("EPISODIC_DIALOGUE")))
                .thenReturn(List.of(row));

        String out = memory(p).retrieve(msg("q")).block();

        assertTrue(out.contains("from-vector"));
        verify(memoryItemRepository, never()).searchByKeyword(any(), anyString(), anyInt(), any(), anyString(), anyBoolean());
    }

    @Test
    void retrieve_vectorMode_emptyUserInput_usesKeywordBrowse() {
        MemoryPolicyDO p = basePolicy();
        p.setRetrievalMode(MemoryPolicySemantic.RETRIEVAL_VECTOR);
        when(memoryVectorIndexService.resolveAggregate(p)).thenReturn(Optional.of(new KbCollectionAggregate()));
        when(memoryItemRepository.searchByKeyword(
                eq("pol-1"), eq(""), eq(5), isNull(), eq("EPISODIC_DIALOGUE"), eq(false)
        )).thenReturn(List.of());

        memory(p).retrieve(msg("   ")).block();

        verify(memoryItemRepository).searchByKeyword(
                eq("pol-1"), eq(""), eq(5), isNull(), eq("EPISODIC_DIALOGUE"), eq(false)
        );
        verify(memoryVectorIndexService, never()).searchByVector(any(), anyString(), anyInt(), any(), any());
    }

    @Test
    void retrieve_hybridMode_mergesVectorThenKeyword() {
        MemoryPolicyDO p = basePolicy();
        p.setRetrievalMode(MemoryPolicySemantic.RETRIEVAL_HYBRID);
        when(memoryVectorIndexService.resolveAggregate(p)).thenReturn(Optional.of(new KbCollectionAggregate()));

        MemoryItemDO v = new MemoryItemDO();
        v.setId("a");
        v.setContent("vec-a");
        MemoryItemDO k1 = new MemoryItemDO();
        k1.setId("a");
        k1.setContent("kw-a");
        MemoryItemDO k2 = new MemoryItemDO();
        k2.setId("b");
        k2.setContent("kw-b");

        when(memoryVectorIndexService.searchByVector(eq(p), eq("q"), eq(5), isNull(), eq("EPISODIC_DIALOGUE")))
                .thenReturn(List.of(v));
        when(memoryItemRepository.searchByKeyword(
                eq("pol-1"), eq("q"), eq(10), isNull(), eq("EPISODIC_DIALOGUE"), eq(true)
        )).thenReturn(List.of(k1, k2));

        String out = memory(p).retrieve(msg("q")).block();

        assertTrue(out.contains("vec-a"));
        assertTrue(out.contains("kw-b"));
        int firstVec = out.indexOf("vec-a");
        int firstKwB = out.indexOf("kw-b");
        assertTrue(firstVec < firstKwB, "向量结果应排在合并列表前");
    }

    @Test
    void retrieve_hybridMode_emptyQuery_skipsVectorMerge() {
        MemoryPolicyDO p = basePolicy();
        p.setRetrievalMode(MemoryPolicySemantic.RETRIEVAL_HYBRID);
        when(memoryVectorIndexService.resolveAggregate(p)).thenReturn(Optional.of(new KbCollectionAggregate()));
        when(memoryItemRepository.searchByKeyword(
                eq("pol-1"), eq(""), eq(5), isNull(), eq("EPISODIC_DIALOGUE"), eq(false)
        )).thenReturn(List.of());

        memory(p).retrieve(msg("")).block();

        verify(memoryVectorIndexService, never()).searchByVector(any(), anyString(), anyInt(), any(), any());
        verify(memoryItemRepository, never()).searchByKeyword(
                eq("pol-1"), eq("q"), anyInt(), any(), anyString(), eq(true)
        );
    }

    @Test
    void retrieve_returnsEmptyWhenNoRows() {
        MemoryPolicyDO p = basePolicy();
        when(memoryItemRepository.searchByKeyword(any(), anyString(), anyInt(), any(), anyString(), anyBoolean()))
                .thenReturn(List.of());

        String out = memory(p).retrieve(msg("x")).block();

        assertTrue(out == null || out.isBlank());
    }

    @Test
    void retrieve_withMemoryNamespace_passesToRepository() {
        MemoryPolicyDO p = basePolicy();
        when(memoryVectorIndexService.resolveAggregate(p)).thenReturn(Optional.empty());
        LegoLongTermMemory mem = new LegoLongTermMemory(
                memoryItemRepository,
                p,
                memoryVectorIndexService,
                "agent-1",
                "  ns1  ",
                480
        );
        when(memoryItemRepository.searchByKeyword(
                eq("pol-1"), eq("hi"), eq(5), eq("ns1"), eq("EPISODIC_DIALOGUE"), eq(true)
        )).thenReturn(List.of());

        mem.retrieve(msg("hi")).block();

        verify(memoryItemRepository).searchByKeyword(
                eq("pol-1"), eq("hi"), eq(5), eq("ns1"), eq("EPISODIC_DIALOGUE"), eq(true)
        );
    }
}
