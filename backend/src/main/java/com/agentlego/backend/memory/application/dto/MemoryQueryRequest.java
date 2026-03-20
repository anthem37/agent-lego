package com.agentlego.backend.memory.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 记忆检索请求 DTO。
 * <p>
 * 说明：
 * - ownerScope：限定检索范围；
 * - queryText：检索查询文本；
 * - topK：返回条数上限。
 */
@Data
public class MemoryQueryRequest {

    /**
     * 归属范围（owner scope），例如 userId。
     */
    @NotBlank
    private String ownerScope;

    /**
     * 查询文本（query）。
     */
    @NotBlank
    private String queryText;

    /**
     * 返回条数上限（topK）。
     */
    @Min(1)
    private int topK = 5;
}

