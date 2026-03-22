package com.agentlego.backend.vectorstore.application.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class VectorStorePointsPreviewDto {
    private String collectionName;
    private List<VectorStorePointPreviewRowDto> rows = new ArrayList<>();
    /**
     * Qdrant scroll 下一页游标；无更多则为 null
     */
    private String nextCursor;
    /**
     * 说明（如 Milvus 暂不支持预览）
     */
    private String hint;
}
