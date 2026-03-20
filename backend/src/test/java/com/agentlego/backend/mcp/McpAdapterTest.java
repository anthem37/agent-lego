package com.agentlego.backend.mcp;

import com.agentlego.backend.tool.application.ToolExecutionService;
import com.agentlego.backend.tool.http.HttpToolRequestExecutor;
import com.agentlego.backend.tool.local.LocalBuiltinToolCatalog;
import com.agentlego.backend.workflow.application.WorkflowApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class McpAdapterTest {

    @Mock
    private WorkflowApplicationService workflowApplicationService;

    @Mock
    private HttpToolRequestExecutor httpToolRequestExecutor;

    @Test
    void buildPlatformMcpServerBundle_shouldExposeBuiltinToolNames() throws Exception {
        McpAdapter adapter = new McpAdapter();
        ObjectMapper objectMapper = new ObjectMapper();
        LocalBuiltinToolCatalog catalog = new LocalBuiltinToolCatalog();
        ToolExecutionService exec = new ToolExecutionService(
                workflowApplicationService, catalog, new McpClientRegistry(), httpToolRequestExecutor, 120);

        PlatformMcpServerBundle bundle = adapter.buildPlatformMcpServerBundle(objectMapper, "/mcp", exec, catalog);
        assertNotNull(bundle);
        assertNotNull(bundle.server());
        assertNotNull(bundle.transport());

        McpSyncServer server = bundle.server();
        Set<String> toolNames = server.listTools().stream()
                .map(McpSchema.Tool::name)
                .collect(Collectors.toSet());

        assertTrue(toolNames.contains("echo"));
        assertTrue(toolNames.contains("now"));
        assertTrue(toolNames.contains("format_line"));
    }
}
