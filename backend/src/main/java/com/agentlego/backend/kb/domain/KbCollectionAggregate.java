package com.agentlego.backend.kb.domain;

import lombok.Data;

import java.time.Instant;

@Data
public class KbCollectionAggregate {
    private String id;
    private String name;
    private String description;
    private String embeddingModelId;
    /**
     * 上游 embedding 输出维度（与 {@link com.agentlego.backend.model.support.ModelEmbeddingDimensions} 一致）。
     */
    private int embeddingDims;
    private Instant createdAt;
    private Instant updatedAt;
}
