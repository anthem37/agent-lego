package com.agentlego.backend.tool.application.service;

import com.agentlego.backend.mcp.client.McpClientRegistry;
import com.agentlego.backend.mcp.properties.McpClientProperties;
import com.agentlego.backend.tool.application.dto.BatchImportMcpToolsRequest;
import com.agentlego.backend.tool.application.dto.BatchImportMcpToolsResponse;
import com.agentlego.backend.tool.application.support.ToolWriteSupport;
import com.agentlego.backend.tool.domain.ToolRepository;
import com.agentlego.backend.tool.local.LocalBuiltinToolCatalog;
import com.agentlego.backend.tool.local.LocalBuiltinTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link McpToolDiscoveryService}：远端列表与批量导入（依赖 {@link ToolCreationService}）。
 */
@ExtendWith(MockitoExtension.class)
class McpToolDiscoveryServiceTest {

    @Mock
    private McpClientRegistry mcpClientRegistry;

    @Mock
    private ToolRepository toolRepository;

    private static LocalBuiltinToolCatalog catalog() {
        return LocalBuiltinToolCatalog.forTests(LocalBuiltinTools.class);
    }

    private McpToolDiscoveryService service() {
        McpClientProperties props = new McpClientProperties();
        ToolWriteSupport write = new ToolWriteSupport(toolRepository);
        ToolCreationService creation = new ToolCreationService(toolRepository, catalog(), write);
        return new McpToolDiscoveryService(
                mcpClientRegistry,
                props,
                new ObjectMapper(),
                toolRepository,
                creation
        );
    }

    @Test
    void listRemoteMcpTools_shouldMapToDto() {
        McpSchema.Tool t = McpSchema.Tool.builder()
                .name("t1")
                .description("desc")
                .build();
        when(mcpClientRegistry.listRemoteTools("http://127.0.0.1:9/sse")).thenReturn(List.of(t));

        var list = service().listRemoteMcpTools("http://127.0.0.1:9/sse", false);
        assertEquals(1, list.size());
        assertEquals("t1", list.get(0).getName());
        assertEquals("desc", list.get(0).getDescription());
    }

    @Test
    void batchImportMcpTools_shouldCreateOneMcpTool() {
        String endpoint = "http://127.0.0.1:7777/mcp";
        McpSchema.Tool remote = McpSchema.Tool.builder()
                .name("remote_tool")
                .description("from server")
                .build();
        when(mcpClientRegistry.listRemoteTools(endpoint)).thenReturn(List.of(remote));
        when(toolRepository.existsOtherWithNameIgnoreCase(anyString(), any())).thenReturn(false);
        when(toolRepository.save(any())).thenAnswer(inv -> {
            com.agentlego.backend.tool.domain.ToolAggregate a = inv.getArgument(0);
            return a.getId();
        });

        BatchImportMcpToolsRequest req = new BatchImportMcpToolsRequest();
        req.setEndpoint(endpoint);
        req.setRemoteToolNames(List.of("remote_tool"));

        BatchImportMcpToolsResponse resp = service().batchImportMcpTools(req);
        assertEquals(1, resp.getCreated().size());
        assertEquals("remote_tool", resp.getCreated().get(0).getRemoteToolName());
        assertTrue(resp.getSkipped().isEmpty());
        assertTrue(resp.getNameConflicts().isEmpty());
        verify(toolRepository, times(1)).save(any());
    }

    @Test
    void batchImportMcpTools_unknownRemoteName_shouldSkip() {
        String endpoint = "http://127.0.0.1:7778/mcp";
        when(mcpClientRegistry.listRemoteTools(endpoint)).thenReturn(List.of());

        BatchImportMcpToolsRequest req = new BatchImportMcpToolsRequest();
        req.setEndpoint(endpoint);
        req.setRemoteToolNames(List.of("nope"));

        BatchImportMcpToolsResponse resp = service().batchImportMcpTools(req);
        assertTrue(resp.getCreated().isEmpty());
        assertEquals(1, resp.getSkipped().size());
        assertTrue(resp.getSkipped().get(0).getReason().contains("不存在"));
        verify(toolRepository, never()).save(any());
    }
}
