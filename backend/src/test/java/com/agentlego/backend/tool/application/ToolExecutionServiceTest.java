package com.agentlego.backend.tool.application;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.mcp.McpClientRegistry;
import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.domain.ToolType;
import com.agentlego.backend.tool.local.LocalBuiltinToolCatalog;
import com.agentlego.backend.workflow.application.WorkflowApplicationService;
import com.agentlego.backend.workflow.application.dto.RunWorkflowRequest;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ToolExecutionService 单元测试。
 * <p>
 * 说明：本组测试覆盖 LOCAL 工具（echo/now）调用的最小闭环与异常分支。
 */
@ExtendWith(MockitoExtension.class)
class ToolExecutionServiceTest {

    @Mock
    private WorkflowApplicationService workflowApplicationService;

    @Mock
    private McpClientRegistry mcpClientRegistry;

    private static LocalBuiltinToolCatalog catalog() {
        try {
            return new LocalBuiltinToolCatalog();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private ToolExecutionService service() {
        return new ToolExecutionService(workflowApplicationService, catalog(), mcpClientRegistry);
    }

    @Test
    void executeLocalTool_echo_shouldEchoText() {
        ToolResultBlock result = service().executeLocalTool(
                        "echo",
                        Map.of("content", "hello")
                )
                .block();

        assertNotNull(result);

        List<ContentBlock> output = result.getOutput();
        assertNotNull(output);

        String echoed = output.stream()
                .filter(b -> b instanceof TextBlock)
                .map(b -> ((TextBlock) b).getText())
                .findFirst()
                .orElse(null);

        assertEquals("hello", echoed);
    }

    @Test
    void executeLocalTool_formatLine_shouldReplacePlaceholders() {
        ToolResultBlock result = service().executeLocalTool(
                        "format_line",
                        Map.of(
                                "template", "{who} 说：{what}",
                                "who", "演示用户",
                                "what", "多入参本地工具 OK"
                        )
                )
                .block();

        assertNotNull(result);
        String text = result.getOutput().stream()
                .filter(b -> b instanceof TextBlock)
                .map(b -> ((TextBlock) b).getText())
                .findFirst()
                .orElse("");
        assertEquals("演示用户 说：多入参本地工具 OK", text);
    }

    @Test
    void executeLocalTool_now_shouldReturnIsoInstant() {
        ToolResultBlock result = service().executeLocalTool("now", Map.of())
                .block();

        assertNotNull(result);

        String text = result.getOutput().stream()
                .filter(b -> b instanceof TextBlock)
                .map(b -> ((TextBlock) b).getText())
                .findFirst()
                .orElse("");

        assertFalse(text.isBlank());
        // LocalNowTool uses Instant#toString which should be parseable.
        assertDoesNotThrow(() -> Instant.parse(text));
    }

    @Test
    void executeLocalTool_unsupportedTool_shouldThrowApiException() {
        ApiException ex = assertThrows(ApiException.class, () ->
                service().executeLocalTool("unknown", Map.of()).block()
        );

        assertEquals("UNSUPPORTED_LOCAL_TOOL", ex.getCode());
    }

    @Test
    void buildToolkit_httpTool_shouldRegisterByName() {
        ToolAggregate t = new ToolAggregate();
        t.setToolType(ToolType.HTTP);
        t.setName("public_api");
        t.setDefinition(Map.of(
                "url", "https://example.com/search?q={q}",
                "method", "GET",
                "description", "Search example"
        ));

        Toolkit tk = service().buildToolkitForToolIds(List.of(t));

        assertTrue(tk.getToolNames().contains("public_api"));
    }

    @Test
    void buildToolkit_httpTool_descriptionAppendsOutputSchemaNarrative() {
        Map<String, Object> userIdProp = new LinkedHashMap<>();
        userIdProp.put("type", "string");
        userIdProp.put("description", "用户标识");
        Map<String, Object> outputSchema = new LinkedHashMap<>();
        outputSchema.put("type", "object");
        outputSchema.put("properties", Map.of("userId", userIdProp));
        outputSchema.put("required", List.of("userId"));

        ToolAggregate t = new ToolAggregate();
        t.setToolType(ToolType.HTTP);
        t.setName("api_with_output");
        t.setDefinition(Map.of(
                "url", "https://example.com/u",
                "method", "GET",
                "description", "Fetch user",
                "outputSchema", outputSchema
        ));

        Toolkit tk = service().buildToolkitForToolIds(List.of(t));
        AgentTool tool = tk.getTool("api_with_output");
        assertNotNull(tool);
        String desc = tool.getDescription();
        assertTrue(desc.contains("Fetch user"));
        assertTrue(desc.contains("【返回说明】"));
        assertTrue(desc.contains("userId"));
        assertTrue(desc.contains("[必有]"));
    }

    @Test
    void buildToolkit_workflowTool_descriptionAppendsOutputSchemaWhenPresent() {
        Map<String, Object> runIdProp = Map.of("type", "string", "description", "运行 ID");
        ToolAggregate t = new ToolAggregate();
        t.setToolType(ToolType.WORKFLOW);
        t.setName("wf_with_output");
        t.setDefinition(Map.of(
                "workflowId", "wf-99",
                "description", "My workflow",
                "outputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of("runId", runIdProp),
                        "required", List.of()
                )
        ));

        Toolkit tk = service().buildToolkitForToolIds(List.of(t));
        String desc = tk.getTool("wf_with_output").getDescription();
        assertTrue(desc.contains("My workflow"));
        assertTrue(desc.contains("【返回说明】"));
        assertTrue(desc.contains("runId"));
        assertTrue(desc.contains("运行 ID"));
    }

    @Test
    void buildToolkit_workflowTool_shouldRegisterByName() {
        ToolAggregate t = new ToolAggregate();
        t.setToolType(ToolType.WORKFLOW);
        t.setName("run_my_wf");
        t.setDefinition(Map.of("workflowId", "wf-1", "description", "d"));

        Toolkit tk = service().buildToolkitForToolIds(List.of(t));

        assertTrue(tk.getToolNames().contains("run_my_wf"));
    }

    @Test
    void executeTool_workflow_shouldCallWorkflowService() {
        when(workflowApplicationService.runWorkflowSynchronously(eq("wf-1"), any(RunWorkflowRequest.class)))
                .thenReturn(Map.of(
                        "runId", "r1",
                        "status", "SUCCEEDED",
                        "output", Map.of("output", "done")
                ));

        ToolAggregate t = new ToolAggregate();
        t.setToolType(ToolType.WORKFLOW);
        t.setName("wf_tool");
        t.setDefinition(Map.of("workflowId", "wf-1"));

        ToolResultBlock block = service().executeTool(t, Map.of("input", "hello")).block();

        assertNotNull(block);
        String text = block.getOutput().stream()
                .filter(b -> b instanceof TextBlock)
                .map(b -> ((TextBlock) b).getText())
                .findFirst()
                .orElse("");
        assertTrue(text.contains("runId"));
        assertTrue(text.contains("r1"));
        verify(workflowApplicationService).runWorkflowSynchronously(eq("wf-1"), any(RunWorkflowRequest.class));
    }
}
