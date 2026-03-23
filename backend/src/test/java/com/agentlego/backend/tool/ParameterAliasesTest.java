package com.agentlego.backend.tool;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParameterAliasesTest {

    @Test
    void toWireInput_renamesRootKeys() {
        Map<String, String> aliases = Map.of("userId", "user_id");
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("userId", "42");
        Map<String, Object> out = ParameterAliases.toWireInput(aliases, in);
        assertEquals("42", out.get("user_id"));
    }

    @Test
    void resolvePlaceholderValue_matchesWireName() {
        Map<String, String> aliases = Map.of("userId", "user_id");
        Map<String, Object> in = Map.of("userId", "x");
        assertEquals("x", ParameterAliases.resolvePlaceholderValue(aliases, in, "user_id"));
        assertEquals("x", ParameterAliases.resolvePlaceholderValue(aliases, in, "userId"));
    }
}
