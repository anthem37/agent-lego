package com.agentlego.backend.memorypolicy.support;

import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryPolicyDO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryPolicySemanticTest {

    @Test
    void implementationWarnings_keywordOff_empty() {
        List<String> w = MemoryPolicySemantic.implementationWarnings("KEYWORD", "OFF");
        assertTrue(w.isEmpty());
    }

    @Test
    void implementationWarnings_vector_hasNote() {
        List<String> w = MemoryPolicySemantic.implementationWarnings("VECTOR", "OFF");
        assertEquals(1, w.size());
        assertTrue(w.get(0).contains("VECTOR"));
    }

    @Test
    void implementationWarnings_vector_configured_noDowngradeNote() {
        List<String> w = MemoryPolicySemantic.implementationWarnings("VECTOR", "OFF", true);
        assertTrue(w.stream().noneMatch(s -> s.contains("降级")));
    }

    @Test
    void isVectorLinkConfigured_true_whenProfileAndCollection() {
        MemoryPolicyDO r = new MemoryPolicyDO();
        r.setRetrievalMode("VECTOR");
        r.setVectorStoreProfileId("p1");
        r.setVectorStoreConfigJson("{\"collectionName\":\"mem_x\"}");
        assertTrue(MemoryPolicySemantic.isVectorLinkConfigured(r));
    }

    @Test
    void implementationWarnings_summary_hasNote() {
        List<String> w = MemoryPolicySemantic.implementationWarnings("KEYWORD", "ASSISTANT_SUMMARY");
        assertEquals(1, w.size());
        assertTrue(w.get(0).contains("ASSISTANT_SUMMARY"));
    }
}
