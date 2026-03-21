package com.agentlego.backend.kb.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 {@link ToolResultBlock} 中提取可供 {@link KbToolPlaceholderExpander} 使用的「根 JSON 对象」。
 * <p>
 * 典型场景：HTTP 工具成功时在 {@link TextBlock} 中返回 JSON 字符串，解析为 Map/List 后按 {@code toolId} 存入会话缓冲。
 */
public final class KbToolResultRootExtractor {

    private KbToolResultRootExtractor() {
    }

    /**
     * @return 可 JSON 序列化为对象的根；无法解析时返回 {@code Map} 包装原始文本；失败/空返回 {@code null}
     */
    public static Object extractRoot(ToolResultBlock block, ObjectMapper om) {
        if (block == null || om == null) {
            return null;
        }
        // agentscope：ToolResultBlock.error() 的 id/name 为 null；成功块通常带 toolUse 关联 id/name
        if (block.getId() == null || block.getName() == null) {
            return null;
        }
        List<ContentBlock> output = block.getOutput();
        if (output == null || output.isEmpty()) {
            return null;
        }
        String text = null;
        for (ContentBlock b : output) {
            if (b instanceof TextBlock tb) {
                String t = tb.getText();
                if (t != null && !t.isBlank()) {
                    text = t.trim();
                    break;
                }
            }
        }
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return om.readValue(text, Object.class);
        } catch (Exception e) {
            Map<String, Object> wrap = new LinkedHashMap<>(2);
            wrap.put("_raw", text);
            return wrap;
        }
    }
}
