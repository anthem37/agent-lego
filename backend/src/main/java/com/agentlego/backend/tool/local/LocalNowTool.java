package com.agentlego.backend.tool.local;

import io.agentscope.core.tool.Tool;

import java.time.Instant;

@LocalBuiltinUiHint(
        label = "now — 当前时间",
        hint = "联调时入参可留空。"
)
public class LocalNowTool {

    @Tool(
            name = "now",
            description = "Return current server time in ISO-8601.",
            converter = PlainTextToolResultConverter.class
    )
    public String now(
            // Some AgentScope tool frameworks model tool input as a single string field `content`.
            // Mark it optional so the tool can be invoked with or without input.
            @io.agentscope.core.tool.ToolParam(
                    name = "content",
                    required = false,
                    description = "Optional content"
            ) String content
    ) {
        return Instant.now().toString();
    }
}

