package com.agentlego.backend.kb.application.dto;

import lombok.Data;

import java.util.Map;

/**
 * 按集合绑定将文档正文中的占位符替换为工具出参预览。
 * <p>
 * {@code toolOutputs}：工具 ID → 该工具一次调用的 JSON 根对象（与运行时注入结构一致）。
 */
@Data
public class RenderKbDocumentRequest {
    private Map<String, Object> toolOutputs;
}
