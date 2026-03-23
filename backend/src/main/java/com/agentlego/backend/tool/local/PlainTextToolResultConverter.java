package com.agentlego.backend.tool.local;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolResultConverter;

import java.lang.reflect.Type;

/**
 * 将工具返回值转为纯文本 {@link ToolResultBlock}，避免默认序列化把 String 变成带额外引号的 JSON 字符串。
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
