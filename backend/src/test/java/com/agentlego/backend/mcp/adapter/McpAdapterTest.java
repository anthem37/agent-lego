package com.agentlego.backend.mcp.adapter;

import com.agentlego.backend.mcp.client.McpClientRegistry;
import com.agentlego.backend.tool.application.dto.LocalBuiltinToolMetaDto;
import com.agentlego.backend.tool.application.service.ToolExecutionService;
import com.agentlego.backend.tool.http.HttpToolRequestExecutor;
import com.agentlego.backend.tool.local.LocalBuiltinToolCatalog;
import com.agentlego.backend.tool.local.LocalBuiltinTools;
import com.agentlego.backend.workflow.application.service.WorkflowApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpSyncServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class McpAdapterTest {

    @Mock
    private WorkflowApplicationService workflowApplicationService;

    @Mock
    private HttpToolRequestExecutor httpToolRequestExecutor;

    @Test
    void buildPlatformMcpServerBundle_shouldRegisterBuiltinToolsFromCatalog() throws Exception {
        McpAdapter adapter = new McpAdapter();
        ObjectMapper objectMapper = new ObjectMapper();
        LocalBuiltinToolCatalog catalog = LocalBuiltinToolCatalog.forTests(LocalBuiltinTools.class);
        ToolExecutionService exec = new ToolExecutionService(
                workflowApplicationService, catalog, new McpClientRegistry(), httpToolRequestExecutor, 120);

        PlatformMcpServerBundle bundle = adapter.buildPlatformMcpServerBundle(objectMapper, "/mcp", exec, catalog.listMeta());
        assertNotNull(bundle);
        assertNotNull(bundle.server());
        assertNotNull(bundle.transport());

        McpSyncServer server = bundle.server();
        int n = catalog.listMeta().size();
        assertEquals(n, server.listTools().size());
    }

    @Test
    void syncLocalBuiltinTools_shouldReflectExposureSubset() {
        McpAdapter adapter = new McpAdapter();
        ObjectMapper objectMapper = new ObjectMapper();
        LocalBuiltinToolCatalog catalog = LocalBuiltinToolCatalog.forTests(LocalBuiltinTools.class);
        ToolExecutionService exec = new ToolExecutionService(
                workflowApplicationService, catalog, new McpClientRegistry(), httpToolRequestExecutor, 120);

        List<LocalBuiltinToolMetaDto> all = catalog.listMeta();
        LocalBuiltinToolMetaDto first = all.get(0);
        List<LocalBuiltinToolMetaDto> one = List.of(first);

        PlatformMcpServerBundle bundle = adapter.buildPlatformMcpServerBundle(objectMapper, "/mcp2", exec, all);
        McpSyncServer server = bundle.server();
        assertEquals(all.size(), server.listTools().size());

        adapter.syncLocalBuiltinTools(server, exec, one);
        assertEquals(1, server.listTools().size());
        assertEquals(first.getName(), server.listTools().get(0).name());

        adapter.syncLocalBuiltinTools(server, exec, all);
        assertEquals(all.size(), server.listTools().size());
    }
}
