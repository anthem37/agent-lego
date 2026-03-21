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
    @NotBlank
    private String embeddingModelId;
    /**
     * 可选；默认 FIXED_WINDOW。见 {@code GET /kb/meta/chunk-strategies}。
     */
    private String chunkStrategy;
    /**
     * 可选；与策略对应的参数，如 maxChars、overlap、separators（字符串数组）。
     */
    private Map<String, Object> chunkParams;
}
