package com.agentlego.backend.vectorstore.application.dto;

import lombok.Data;

/**
 * 连接探测：连通性、版本、集合数量等。
 */
@Data
public class VectorStoreProbeResultDto {
    private boolean ok;
    private String message;
    /**
     * 往返耗时（毫秒）
     */
    private long latencyMs;
    /**
     * Milvus / Qdrant 版本或标识
     */
    private String serverVersion;
    /**
     * 健康检查摘要（若有）
     */
    private String healthSummary;
    /**
     * 当前库下可见的 collection 数量
     */
    private Integer collectionCount;
}
