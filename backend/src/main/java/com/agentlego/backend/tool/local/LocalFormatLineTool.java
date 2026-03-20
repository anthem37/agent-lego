package com.agentlego.backend.tool.local;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 * 演示用内置工具：多入参 + 文本出参，便于前端展示「入参 / 出参」契约与联调。
 */
@LocalBuiltinUiHint(
        label = "format_line — 模板拼接（多入参示例）",
        hint = "联调示例：template 填「{who} 说：{what}」，who 填「小明」，what 可填「你好」或留空。"
)
public class LocalFormatLineTool {

    @Tool(
            name = "format_line",
            description = "Replace placeholders {who} and {what} in a template string.",
            converter = PlainTextToolResultConverter.class
    )
    public String formatLine(
            @ToolParam(
                    name = "template",
                    required = true,
                    description = "模板，可使用占位符 {who}、{what}"
            ) String template,
            @ToolParam(
                    name = "who",
                    required = true,
                    description = "替换 {who}"
            ) String who,
            @ToolParam(
                    name = "what",
                    required = false,
                    description = "替换 {what}；省略时按空字符串处理"
            ) String what
    ) {
        String t = template != null ? template : "";
        String wWho = who != null ? who : "";
        String wWhat = what != null ? what : "";
        return t.replace("{who}", wWho).replace("{what}", wWhat);
    }
}
