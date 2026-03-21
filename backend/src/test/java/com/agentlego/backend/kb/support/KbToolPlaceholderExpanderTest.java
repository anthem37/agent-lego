package com.agentlego.backend.kb.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KbToolPlaceholderExpanderTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Test
    void expand_replacesNestedPath() {
        String bindings = "{\"mappings\":[{\"placeholder\":\"orderNo\",\"toolId\":\"t1\",\"jsonPath\":\"$.data.orderNo\"}]}";
        String body = "单号：{{orderNo}} 结束";
        Map<String, Object> out = Map.of(
                "t1",
                Map.of("data", Map.of("orderNo", "A-100"))
        );
        String r = KbToolPlaceholderExpander.expand(body, bindings, out, OM);
        assertEquals("单号：A-100 结束", r);
    }

    @Test
    void resolveJsonPath_arrayIndex() {
        var root = OM.createArrayNode().add("x").add("y");
        assertEquals("y", KbToolPlaceholderExpander.resolveJsonPath(root, "$.1").asText());
    }

    @Test
    void resolveJsonPath_missingReturnsNull() {
        assertNull(KbToolPlaceholderExpander.resolveJsonPath(OM.createObjectNode(), "$.a.b"));
    }
}
