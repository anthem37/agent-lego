package com.agentlego.backend.kb.application.dto;

import lombok.Data;

@Data
public class RenderKbDocumentResponse {
    /**
     * 替换占位符后的正文（不入库）。
     */
    private String renderedBody;
}
