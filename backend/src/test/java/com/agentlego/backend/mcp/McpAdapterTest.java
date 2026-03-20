package com.agentlego.backend.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpAdapterTest {

    @Test
    void buildPlatformMcpServer_shouldExposeEchoAndNowTools() {
        McpAdapter adapter = new McpAdapter();
        ObjectMapper objectMapper = new ObjectMapper();

        McpSyncServer server = adapter.buildPlatformMcpServer(objectMapper, "/mcp");
        assertNotNull(server);

        Set<String> toolNames = server.listTools().stream()
                .map(McpSchema.Tool::name)
                .collect(Collectors.toSet());

        assertTrue(toolNames.contains("echo"));
        assertTrue(toolNames.contains("now"));
    }
}

