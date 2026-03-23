package com.agentlego.backend.memorypolicy.application.dto;

import lombok.Data;

/**
 * {@code POST /memory-policies/{id}/reindex-vectors} 响应体。
 */
@Data
public class MemoryReindexVectorsResultDto {
    /**
     * 已提交重新嵌入并 upsert 的条目数。
     */
    private int indexedCount;
}
