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
    /**
     * 外置向量库类型，与 {@link KbVectorStoreKind#name()} 一致。
     */
    private String vectorStoreKind = KbVectorStoreKind.MILVUS.name();
    /**
     * 向量库连接与物理集合等配置 JSON（如 Milvus 的 host、collectionName）。
     */
    private String vectorStoreConfigJson = "{}";
    /**
     * 可选：引用 {@code lego_vector_store_profiles}（创建时写入，读路径使用已落库的合并配置）。
     */
    private String vectorStoreProfileId;
    /**
     * 分片策略编码，与 {@link KbChunkStrategyKind#name()} 一致。
     */
    private String chunkStrategy = "FIXED_WINDOW";
    /**
     * 分片参数 JSON（maxChars、overlap、可选 separators）。
     */
    private String chunkParamsJson = "{\"maxChars\":900,\"overlap\":120}";
    private Instant createdAt;
    private Instant updatedAt;
}
