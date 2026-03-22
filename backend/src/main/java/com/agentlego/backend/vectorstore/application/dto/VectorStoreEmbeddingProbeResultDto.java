package com.agentlego.backend.vectorstore.application.dto;

import lombok.Data;

@Data
public class VectorStoreEmbeddingProbeResultDto {
    private boolean ok;
    private String embeddingModelId;
    private int vectorDimension;
    /**
     * 与 profile 声明维度是否一致
     */
    private boolean dimensionMatchesProfile;
    /**
     * L2 范数（用于粗验向量是否正常）
     */
    private double vectorNorm;
    private String message;
}
