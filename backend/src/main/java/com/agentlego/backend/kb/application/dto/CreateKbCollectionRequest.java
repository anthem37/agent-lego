package com.agentlego.backend.kb.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class CreateKbCollectionRequest {
    @NotBlank
    @Size(max = 256)
    private String name;
    private String description;
    /**
     * 可选；若填写须与公共向量库 profile 中的嵌入模型一致。通常省略，由 {@link #vectorStoreProfileId} 对应 profile 决定。
     */
    private String embeddingModelId;
    /**
     * 必填：引用平台公共向量库 {@code lego_vector_store_profiles}（不可内联 host/port 等连接信息）。
     */
    private String vectorStoreProfileId;
    /**
     * 已废弃于创建路径：向量库类型由 {@link #vectorStoreProfileId} 对应 profile 决定，请勿单独传。
     */
    private String vectorStoreKind;
    /**
     * 仅允许键 {@code collectionName}，用于覆盖物理集合名；连接信息一律来自公共 profile。
     */
    private Map<String, Object> vectorStoreConfig;
    /**
     * 可选；默认 FIXED_WINDOW。见 {@code GET /kb/meta/chunk-strategies}。
     */
    private String chunkStrategy;
    /**
     * 可选；与策略对应的参数，如 maxChars、overlap、separators（字符串数组）。
     */
    private Map<String, Object> chunkParams;
}
