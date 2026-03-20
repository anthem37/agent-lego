package com.agentlego.backend.tool.local;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

@LocalBuiltinUiHint(
        label = "echo — 回显文本",
        hint = "联调时在「入参」里填参数名 content，取值为你想回显的句子。"
)
public class LocalEchoTool {

    @Tool(
            name = "echo",
            description = "Echo the provided text.",
            converter = PlainTextToolResultConverter.class
    )
    public String echo(
            // AgentScope local-tool framework uses a single string input called `content`.
            // We keep the name stable so ToolExecutionService can pass a consistent schema.
            @ToolParam(name = "content", required = true, description = "Text to echo") String content) {
        return content;
    }
}

