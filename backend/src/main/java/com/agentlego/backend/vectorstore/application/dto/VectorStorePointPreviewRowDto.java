package com.agentlego.backend.vectorstore.application.dto;

import lombok.Data;

import java.util.Map;

/**
 * 向量点抽样预览（payload 为主，向量默认不返回以减小体积）。
 */
@Data
public class VectorStorePointPreviewRowDto {
    private String id;
    /**
     * payload 键值（大文本可能被截断）
     */
    private Map<String, Object> payload;
}
