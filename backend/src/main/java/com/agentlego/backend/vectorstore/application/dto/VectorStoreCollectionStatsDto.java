package com.agentlego.backend.vectorstore.application.dto;

import lombok.Data;

import java.util.Map;

@Data
public class VectorStoreCollectionStatsDto {
    private String collectionName;
    /**
     * 向量条数 / points 数（以各引擎返回为准）
     */
    private Long rowCount;
    /**
     * 原始统计键值（便于排障）
     */
    private Map<String, String> rawStats;
}
