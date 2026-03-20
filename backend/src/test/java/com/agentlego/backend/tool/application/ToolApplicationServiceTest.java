package com.agentlego.backend.tool.application;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.tool.application.dto.CreateToolRequest;
import com.agentlego.backend.tool.application.dto.TestToolCallRequest;
import com.agentlego.backend.tool.application.dto.TestToolCallResponse;
import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.domain.ToolRepository;
import com.agentlego.backend.tool.domain.ToolType;
import io.agentscope.core.message.ToolResultBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ToolApplicationService 单元测试。
 * <p>
 * 覆盖点：
 * - createTool 的参数校验（toolType 缺失/非法）
 * - getTool not found
 * - testToolCall（LOCAL 正常分支 / MCP 不支持分支）
 */
@ExtendWith(MockitoExtension.class)
class ToolApplicationServiceTest {

    @Mock
    private ToolRepository toolRepository;

    @Mock
    private ToolExecutionService toolExecutionService;

    @Test
    void createTool_missingToolType_shouldThrowValidationError() {
        ToolApplicationService service = new ToolApplicationService(toolRepository, toolExecutionService);

        CreateToolRequest req = new CreateToolRequest();
        req.setToolType(null);
        req.setName("t1");

        ApiException ex = assertThrows(ApiException.class, () -> service.createTool(req));
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    @Test
    void createTool_invalidToolType_shouldThrowValidationError() {
        ToolApplicationService service = new ToolApplicationService(toolRepository, toolExecutionService);

        CreateToolRequest req = new CreateToolRequest();
        req.setToolType("INVALID");
        req.setName("t1");

        ApiException ex = assertThrows(ApiException.class, () -> service.createTool(req));
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    @Test
    void getTool_notFound_shouldThrowNotFound() {
        when(toolRepository.findById("missing")).thenReturn(Optional.empty());
        ToolApplicationService service = new ToolApplicationService(toolRepository, toolExecutionService);

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
        when(toolExecutionService.executeLocalTool(eq("echo"), anyMap()))
                .thenReturn(Mono.just(ToolResultBlock.text("ok")));

        ToolApplicationService service = new ToolApplicationService(toolRepository, toolExecutionService);

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

        verify(toolExecutionService, times(1)).executeLocalTool(eq("echo"), anyMap());
    }

    @Test
    void testToolCall_mcpTool_shouldThrowUnsupportedToolType() {
        ToolAggregate tool = new ToolAggregate();
        tool.setId("tool1");
        tool.setToolType(ToolType.MCP);
        tool.setName("mcp-tool");

        when(toolRepository.findById("tool1")).thenReturn(Optional.of(tool));

        ToolApplicationService service = new ToolApplicationService(toolRepository, toolExecutionService);
        ApiException ex = assertThrows(ApiException.class,
                () -> service.testToolCall("tool1", new TestToolCallRequest()));
        assertEquals("UNSUPPORTED_TOOL_TYPE", ex.getCode());
        verifyNoInteractions(toolExecutionService);
    }
}

