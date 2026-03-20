package com.agentlego.backend.tool.application;

import com.agentlego.backend.api.ApiException;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolExecutionService 单元测试。
 * <p>
 * 说明：本组测试覆盖 LOCAL 工具（echo/now）调用的最小闭环与异常分支。
 */
class ToolExecutionServiceTest {

    @Test
    void executeLocalTool_echo_shouldEchoText() {
        ToolExecutionService service = new ToolExecutionService();

        ToolResultBlock result = service.executeLocalTool(
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
    void executeLocalTool_now_shouldReturnIsoInstant() {
        ToolExecutionService service = new ToolExecutionService();

        ToolResultBlock result = service.executeLocalTool("now", Map.of())
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
        ToolExecutionService service = new ToolExecutionService();

        ApiException ex = assertThrows(ApiException.class, () ->
                service.executeLocalTool("unknown", Map.of()).block()
        );

        assertEquals("UNSUPPORTED_LOCAL_TOOL", ex.getCode());
    }
}

