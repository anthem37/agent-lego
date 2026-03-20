package com.agentlego.backend.memory.application.dto;

import lombok.Data;

import java.util.List;

/**
 * 记忆检索响应 DTO。
 */
@Data
public class MemoryQueryResponse {
    /**
     * 命中的记忆条目列表。
     */
    private List<MemoryItemDto> items;
}

