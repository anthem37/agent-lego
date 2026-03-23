package com.agentlego.backend.tool.local;

import com.agentlego.backend.api.ApiException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalBuiltinToolCatalogTest {

    private static final int BUILTIN_COUNT = 5;

    @Test
    void listMeta_shouldExposeBuiltins() {
        LocalBuiltinToolCatalog catalog = LocalBuiltinToolCatalog.forTests(LocalBuiltinTools.class);
        assertEquals(BUILTIN_COUNT, catalog.listMeta().size());
        assertTrue(catalog.findMetaByCanonicalName("time_now").isPresent());
        assertTrue(catalog.findMetaByCanonicalName("hash_sha256").isPresent());
    }

    @Test
    void plainTextBuiltin_outputSchema_shouldBeStringNotPseudoObject() {
        LocalBuiltinToolCatalog catalog = LocalBuiltinToolCatalog.forTests(LocalBuiltinTools.class);
        var meta = catalog.findMetaByCanonicalName("json_format").orElseThrow();
        Map<String, Object> out = meta.getOutputSchema();
        assertEquals("string", out.get("type"));
        String desc = String.valueOf(out.get("description"));
        assertTrue(desc.contains("纯文本") || desc.contains("ToolResultBlock"));
        assertTrue(out.get("properties") == null);
    }

    @Test
    void newInstance_known_shouldReturnHost() {
        LocalBuiltinToolCatalog catalog = LocalBuiltinToolCatalog.forTests(LocalBuiltinTools.class);
        assertNotNull(catalog.newInstance("time_now"));
    }

    @Test
    void newInstance_unknown_shouldThrow() {
        LocalBuiltinToolCatalog catalog = LocalBuiltinToolCatalog.forTests(LocalBuiltinTools.class);
        assertThrows(ApiException.class, () -> catalog.newInstance("anything"));
    }
}
