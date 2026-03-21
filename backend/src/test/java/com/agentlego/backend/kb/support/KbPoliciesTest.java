package com.agentlego.backend.kb.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KbPoliciesTest {

    @Test
    void withoutCollectionId_removesOne_keepsOthers() {
        Map<String, Object> policy = Map.of(
                "collectionIds", List.of("a", "b", "c"),
                "topK", 5
        );
        Map<String, Object> next = KbPolicies.withoutCollectionId(policy, "b");
        assertEquals(List.of("a", "c"), next.get("collectionIds"));
        assertEquals(5, next.get("topK"));
    }

    @Test
    void withoutCollectionId_lastRemoved_returnsEmpty() {
        Map<String, Object> policy = Map.of("collectionIds", List.of("only"));
        Map<String, Object> next = KbPolicies.withoutCollectionId(policy, "only");
        assertTrue(next.isEmpty());
    }

    @Test
    void withoutCollectionId_unknownId_unchangedList() {
        Map<String, Object> policy = Map.of("collectionIds", List.of("x"));
        Map<String, Object> next = KbPolicies.withoutCollectionId(policy, "y");
        assertEquals(List.of("x"), next.get("collectionIds"));
    }

    @Test
    void withoutCollectionId_noCollectionIdsKey_returnsCopy() {
        Map<String, Object> policy = new java.util.LinkedHashMap<>();
        policy.put("topK", 3);
        Map<String, Object> next = KbPolicies.withoutCollectionId(policy, "any");
        assertEquals(3, next.get("topK"));
        assertEquals(1, next.size());
    }
}
