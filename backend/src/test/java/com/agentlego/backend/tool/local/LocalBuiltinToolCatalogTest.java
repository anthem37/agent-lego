package com.agentlego.backend.tool.local;

import com.agentlego.backend.tool.application.dto.LocalBuiltinToolMetaDto;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class LocalBuiltinToolCatalogTest {

    @Test
    void listMeta_shouldIncludeEchoAndNow() throws Exception {
        LocalBuiltinToolCatalog catalog = new LocalBuiltinToolCatalog();
        Set<String> names = catalog.listMeta().stream()
                .map(m -> m.getName().toLowerCase())
                .collect(Collectors.toSet());
        assertTrue(names.contains("echo"));
        assertTrue(names.contains("now"));
        assertTrue(names.contains("format_line"));
        assertNotNull(catalog.newInstance("echo"));
        assertNotNull(catalog.newInstance("NOW"));

        Optional<LocalBuiltinToolMetaDto> echo = catalog.listMeta().stream()
                .filter(m -> "echo".equalsIgnoreCase(m.getName()))
                .findFirst();
        assertTrue(echo.isPresent());
        assertEquals(1, echo.get().getInputParameters().size());
        assertEquals("content", echo.get().getInputParameters().get(0).getName());
        assertTrue(echo.get().getInputParameters().get(0).isRequired());
        assertEquals("String", echo.get().getOutputJavaType());
        assertFalse(echo.get().getOutputDescription().isBlank());

        Optional<LocalBuiltinToolMetaDto> now = catalog.listMeta().stream()
                .filter(m -> "now".equalsIgnoreCase(m.getName()))
                .findFirst();
        assertTrue(now.isPresent());
        assertEquals(1, now.get().getInputParameters().size());
        assertFalse(now.get().getInputParameters().get(0).isRequired());

        Optional<LocalBuiltinToolMetaDto> fmt = catalog.listMeta().stream()
                .filter(m -> "format_line".equalsIgnoreCase(m.getName()))
                .findFirst();
        assertTrue(fmt.isPresent());
        assertEquals(3, fmt.get().getInputParameters().size());
        assertTrue(fmt.get().getInputParameters().stream().anyMatch(p -> "template".equals(p.getName()) && p.isRequired()));
        assertTrue(fmt.get().getInputParameters().stream().anyMatch(p -> "what".equals(p.getName()) && !p.isRequired()));
    }
}
