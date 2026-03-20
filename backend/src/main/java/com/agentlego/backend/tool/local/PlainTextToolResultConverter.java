package com.agentlego.backend.tool.local;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolResultConverter;

import java.lang.reflect.Type;

/**
 * 将工具返回值转换成纯文本的 ToolResultBlock。
 * <p>
 * 背景：
 * - AgentScope 默认的 DefaultToolResultConverter 会把 String 序列化成 JSON string（例如 "\"hello\""）。
 * - 这会让平台侧的工具调用结果、以及后续拼接进 prompt 的文本出现额外引号，影响可读性与下游处理。
 */
public class PlainTextToolResultConverter implements ToolResultConverter {

    @Override
    public ToolResultBlock convert(Object value, Type type) {
        if (value == null) {
            return ToolResultBlock.text("");
        }
        return ToolResultBlock.text(String.valueOf(value));
    }
}

