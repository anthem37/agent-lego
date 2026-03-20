package com.agentlego.backend.tool.application;

import com.agentlego.backend.agent.domain.AgentRepository;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.tool.application.dto.CreateToolRequest;
import com.agentlego.backend.tool.application.dto.TestToolCallRequest;
import com.agentlego.backend.tool.application.dto.TestToolCallResponse;
import com.agentlego.backend.tool.application.dto.UpdateToolRequest;
import com.agentlego.backend.mcp.McpClientProperties;
import com.agentlego.backend.mcp.McpClientRegistry;
import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.local.LocalBuiltinToolCatalog;
import com.agentlego.backend.tool.domain.ToolRepository;
import com.agentlego.backend.tool.domain.ToolType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ToolResultBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ToolApplicationService 单元测试。
 * <p>
 * 覆盖点：
 * - createTool 的参数校验（toolType 缺失/非法）
 * - getTool not found
 * - testToolCall（LOCAL / HTTP / MCP / WORKFLOW 等分支）
 */
@ExtendWith(MockitoExtension.class)
class ToolApplicationServiceTest {

    @Mock
    private ToolRepository toolRepository;

    @Mock
    private ToolExecutionService toolExecutionService;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private McpClientRegistry mcpClientRegistry;

    private static LocalBuiltinToolCatalog localCatalog() {
        try {
            return new LocalBuiltinToolCatalog();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private ToolApplicationService service() {
        McpClientProperties mcpClientProperties = new McpClientProperties();
        return new ToolApplicationService(
                toolRepository,
                toolExecutionService,
                agentRepository,
                localCatalog(),
                mcpClientRegistry,
                new ObjectMapper(),
                mcpClientProperties
        );
    }

    @Test
    void createTool_missingToolType_shouldThrowValidationError() {
        ToolApplicationService service = service();

        CreateToolRequest req = new CreateToolRequest();
        req.setToolType(null);
        req.setName("t1");

        ApiException ex = assertThrows(ApiException.class, () -> service.createTool(req));
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    @Test
    void createTool_invalidToolType_shouldThrowValidationError() {
        ToolApplicationService service = service();

        CreateToolRequest req = new CreateToolRequest();
        req.setToolType("INVALID");
        req.setName("t1");

        ApiException ex = assertThrows(ApiException.class, () -> service.createTool(req));
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    @Test
    void getTool_notFound_shouldThrowNotFound() {
        when(toolRepository.findById("missing")).thenReturn(Optional.empty());
        ToolApplicationService service = service();

        ApiException ex = assertThrows(ApiException.class, () -> service.getTool("missing"));
        assertEquals("NOT_FOUND", ex.getCode());
    }

    @Test
    void testToolCall_localTool_shouldDelegateToToolExecutionService() {
        ToolAggregate tool = new ToolAggregate();
        tool.setId("tool1");
        tool.setToolType(ToolType.LOCAL);
        tool.setName("echo");

        when(toolRepository.findById("tool1")).thenReturn(Optional.of(tool));
        when(toolExecutionService.executeTool(eq(tool), anyMap()))
                .thenReturn(Mono.just(ToolResultBlock.text("ok")));

        ToolApplicationService service = service();

        TestToolCallRequest req = new TestToolCallRequest();
        req.setInput(Map.of("text", "hello"));

        TestToolCallResponse resp = service.testToolCall("tool1", req);

        assertNotNull(resp);
        assertTrue(resp.getResult() instanceof ToolResultBlock);
        ToolResultBlock block = (ToolResultBlock) resp.getResult();
        String echoed = block.getOutput().stream()
                .filter(b -> b instanceof io.agentscope.core.message.TextBlock)
                .map(b -> ((io.agentscope.core.message.TextBlock) b).getText())
                .findFirst()
                .orElse("");
        assertEquals("ok", echoed);

        verify(toolExecutionService, times(1)).executeTool(eq(tool), anyMap());
    }

    @Test
    void testToolCall_httpTool_shouldDelegateToToolExecutionService() {
        ToolAggregate tool = new ToolAggregate();
        tool.setId("tool-http");
        tool.setToolType(ToolType.HTTP);
        tool.setName("weather");
        tool.setDefinition(Map.of("url", "https://example.com", "method", "GET"));

        when(toolRepository.findById("tool-http")).thenReturn(Optional.of(tool));
        when(toolExecutionService.executeTool(eq(tool), anyMap()))
                .thenReturn(Mono.just(ToolResultBlock.text("{\"ok\":true}")));

        ToolApplicationService service = service();

        TestToolCallResponse resp = service.testToolCall("tool-http", new TestToolCallRequest());
        assertNotNull(resp.getResult());
        verify(toolExecutionService, times(1)).executeTool(eq(tool), anyMap());
    }

    @Test
    void testToolCall_mcpTool_shouldDelegateToToolExecutionService() {
        ToolAggregate tool = new ToolAggregate();
        tool.setId("tool1");
        tool.setToolType(ToolType.MCP);
        tool.setName("mcp-tool");
        tool.setDefinition(Map.of("endpoint", "http://localhost:9999/sse"));

        when(toolRepository.findById("tool1")).thenReturn(Optional.of(tool));
        when(toolExecutionService.executeTool(eq(tool), anyMap()))
                .thenReturn(Mono.just(ToolResultBlock.text("mcp-ok")));

        ToolApplicationService service = service();

        TestToolCallResponse resp = service.testToolCall("tool1", new TestToolCallRequest());
        assertNotNull(resp.getResult());
        verify(toolExecutionService, times(1)).executeTool(eq(tool), anyMap());
    }

    @Test
    void testToolCall_workflowTool_shouldDelegateToToolExecutionService() {
        ToolAggregate tool = new ToolAggregate();
        tool.setId("tool-wf");
        tool.setToolType(ToolType.WORKFLOW);
        tool.setName("wf-tool");
        tool.setDefinition(Map.of("workflowId", "wf-1"));

        when(toolRepository.findById("tool-wf")).thenReturn(Optional.of(tool));
        when(toolExecutionService.executeTool(eq(tool), anyMap()))
                .thenReturn(Mono.just(ToolResultBlock.text("{\"runId\":\"r1\"}")));

        ToolApplicationService service = service();

        TestToolCallResponse resp = service.testToolCall("tool-wf", new TestToolCallRequest());
        assertNotNull(resp.getResult());
        verify(toolExecutionService, times(1)).executeTool(eq(tool), anyMap());
    }

    @Test
    void createTool_http_missingUrl_shouldThrowValidationError() {
        ToolApplicationService service = service();

        CreateToolRequest req = new CreateToolRequest();
        req.setToolType("HTTP");
        req.setName("api");
        req.setDefinition(Map.of("description", "no url"));

        ApiException ex = assertThrows(ApiException.class, () -> service.createTool(req));
        assertEquals("VALIDATION_ERROR", ex.getCode());
        verifyNoInteractions(toolRepository);
    }

    @Test
    void createTool_mcp_missingEndpoint_shouldThrowValidationError() {
        ToolApplicationService service = service();

        CreateToolRequest req = new CreateToolRequest();
        req.setToolType("MCP");
        req.setName("ext_mcp");
        req.setDefinition(Map.of("description", "no endpoint"));

        ApiException ex = assertThrows(ApiException.class, () -> service.createTool(req));
        assertEquals("VALIDATION_ERROR", ex.getCode());
        verifyNoInteractions(toolRepository);
    }

    @Test
    void createTool_workflow_missingWorkflowId_shouldThrowValidationError() {
        ToolApplicationService service = service();

        CreateToolRequest req = new CreateToolRequest();
        req.setToolType("WORKFLOW");
        req.setName("wf");
        req.setDefinition(Map.of());

        ApiException ex = assertThrows(ApiException.class, () -> service.createTool(req));
        assertEquals("VALIDATION_ERROR", ex.getCode());
        verifyNoInteractions(toolRepository);
    }

    @Test
    void createTool_duplicateName_shouldThrowConflict() {
        when(toolRepository.existsByToolTypeAndName(ToolType.LOCAL, "echo")).thenReturn(true);
        ToolApplicationService service = service();

        CreateToolRequest req = new CreateToolRequest();
        req.setToolType("LOCAL");
        req.setName("echo");
        req.setDefinition(Map.of());

        ApiException ex = assertThrows(ApiException.class, () -> service.createTool(req));
        assertEquals("CONFLICT", ex.getCode());
        verify(toolRepository, never()).save(any());
    }

    @Test
    void createTool_local_ok_shouldSave() {
        when(toolRepository.existsByToolTypeAndName(ToolType.LOCAL, "echo")).thenReturn(false);
        when(toolRepository.save(any(ToolAggregate.class))).thenAnswer(inv -> inv.getArgument(0, ToolAggregate.class).getId());

        CreateToolRequest req = new CreateToolRequest();
        req.setToolType("LOCAL");
        req.setName("echo");
        req.setDefinition(Map.of());

        String id = service().createTool(req);
        assertNotNull(id);
        verify(toolRepository).save(any(ToolAggregate.class));
    }

    @Test
    void createTool_local_unknownBuiltin_shouldThrowValidationError() {
        ToolApplicationService service = service();

        CreateToolRequest req = new CreateToolRequest();
        req.setToolType("LOCAL");
        req.setName("not_a_builtin");
        req.setDefinition(Map.of());

        ApiException ex = assertThrows(ApiException.class, () -> service.createTool(req));
        assertEquals("VALIDATION_ERROR", ex.getCode());
        verifyNoInteractions(toolRepository);
    }

    @Test
    void updateTool_ok_shouldCallRepositoryUpdate() {
        ToolAggregate existing = new ToolAggregate();
        existing.setId("t1");
        existing.setToolType(ToolType.HTTP);
        existing.setName("old");
        existing.setDefinition(Map.of("url", "https://a.com", "method", "GET"));

        when(toolRepository.findById("t1")).thenReturn(Optional.of(existing));
        when(toolRepository.existsByToolTypeAndNameExcludingId(ToolType.HTTP, "newname", "t1")).thenReturn(false);

        ToolApplicationService service = service();

        UpdateToolRequest req = new UpdateToolRequest();
        req.setToolType("HTTP");
        req.setName("newname");
        req.setDefinition(Map.of("url", "https://b.com", "method", "GET"));

        service.updateTool("t1", req);

        verify(toolRepository).update(any(ToolAggregate.class));
    }

    @Test
    void deleteTool_notFound_shouldThrow() {
        when(toolRepository.findById("x")).thenReturn(Optional.empty());
        ToolApplicationService service = service();
        ApiException ex = assertThrows(ApiException.class, () -> service.deleteTool("x"));
        assertEquals("NOT_FOUND", ex.getCode());
        verify(toolRepository, never()).deleteById(any());
    }

    @Test
    void deleteTool_whenReferencedByAgent_shouldThrowConflict() {
        ToolAggregate tool = new ToolAggregate();
        tool.setId("t1");
        tool.setToolType(ToolType.LOCAL);
        tool.setName("echo");
        when(toolRepository.findById("t1")).thenReturn(Optional.of(tool));
        when(agentRepository.countByToolId("t1")).thenReturn(1);

        ToolApplicationService service = service();
        ApiException ex = assertThrows(ApiException.class, () -> service.deleteTool("t1"));
        assertEquals("CONFLICT", ex.getCode());
        verify(toolRepository, never()).deleteById(any());
    }

    @Test
    void getToolReferences_shouldReturnCounts() {
        ToolAggregate tool = new ToolAggregate();
        tool.setId("t1");
        when(toolRepository.findById("t1")).thenReturn(Optional.of(tool));
        when(agentRepository.countByToolId("t1")).thenReturn(2);
        when(agentRepository.listAgentIdsByToolId("t1")).thenReturn(List.of("a1", "a2"));

        var dto = service().getToolReferences("t1");
        assertEquals(2, dto.getReferencingAgentCount());
        assertEquals(List.of("a1", "a2"), dto.getReferencingAgentIds());
    }

    @Test
    void listToolTypeMeta_shouldReturnFourEntries() {
        assertEquals(4, service().listToolTypeMeta().size());
    }
}

