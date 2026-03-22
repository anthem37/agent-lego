package com.agentlego.backend.vectorstore.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 删除物理 collection（危险操作）：两次名称须完全一致。
 */
@Data
public class DropVectorStoreCollectionRequest {
    @NotBlank
    private String collectionName;
    /**
     * 须与 collectionName 完全相同，用于防误删
     */
    @NotBlank
    private String confirmCollectionName;
}
