package com.agentlego.backend.vectorstore.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class CreateVectorStoreProfileRequest {
    @NotBlank
    @Size(max = 256)
    private String name;
    /**
     * MILVUS | QDRANT
     */
    @NotBlank
    private String vectorStoreKind;
    /**
     * 连接信息（host/port 等）；collectionName 可选，物理集合名通常在知识库创建或记忆写入时单独指定。
     */
    private Map<String, Object> vectorStoreConfig;
    @NotBlank
    private String embeddingModelId;
}
