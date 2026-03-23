package com.agentlego.backend.tool.http;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpToolSpecAliasTest {

    @Test
    void resolveUrl_placeholderUsesAliasFromModelInput() {
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("url", "https://example.com/{user_id}");
        def.put("method", "GET");
        Map<String, Object> aliases = new LinkedHashMap<>();
        aliases.put("userId", "user_id");
        def.put("parameterAliases", aliases);

        HttpToolSpec spec = HttpToolSpec.fromDefinition(def);
        String url = spec.resolveUrl(Map.of("userId", "abc"));
        assertEquals("https://example.com/abc", url);
    }
}
