package com.agentlego.backend.tool.http;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolCallParam;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpToolResultMapperTest {

    private static ToolCallParam dummyParam() {
        ToolUseBlock tub = ToolUseBlock.builder()
                .id("use-1")
                .name("my_http_tool")
                .content("{}")
                .input(Map.of())
                .build();
        return ToolCallParam.builder()
                .toolUseBlock(tub)
                .input(Map.of())
                .build();
    }

    private static String firstText(ToolResultBlock block) {
        return block.getOutput().stream()
                .filter(b -> b instanceof TextBlock)
                .map(b -> ((TextBlock) b).getText())
                .findFirst()
                .orElse("");
    }

    @Test
    void failure_shouldBecomeErrorBlock() {
        ToolResultBlock block = HttpToolResultMapper.toToolResultBlock(
                dummyParam(),
                new HttpToolExecutionResult.Failure("bad")
        );
        assertNotNull(block);
        assertTrue(firstText(block).contains("bad"));
    }

    @Test
    void success_shouldPreserveToolUseIdentityAndMeta() {
        ToolResultBlock block = HttpToolResultMapper.toToolResultBlock(
                dummyParam(),
                new HttpToolExecutionResult.Success(201, "{\"a\":1}", "application/json", "https://ex/p", "POST")
        );
        assertEquals("use-1", block.getId());
        assertEquals("my_http_tool", block.getName());
        assertNotNull(block.getMetadata());
        assertEquals(201, block.getMetadata().get("statusCode"));
        assertEquals("POST", block.getMetadata().get("method"));
        assertEquals("https://ex/p", block.getMetadata().get("url"));
        assertEquals("application/json", block.getMetadata().get("contentType"));
        assertEquals("{\"a\":1}", firstText(block));
    }
}
