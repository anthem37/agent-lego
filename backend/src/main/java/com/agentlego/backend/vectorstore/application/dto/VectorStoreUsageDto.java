package com.agentlego.backend.vectorstore.application.dto;

import lombok.Data;

import java.util.List;

/**
 * 平台内引用：知识库集合按物理 collection 绑定。
 */
@Data
public class VectorStoreUsageDto {
    private int kbCollectionCount;
    private List<KbCollectionRefDto> kbCollections;

    @Data
    public static class KbCollectionRefDto {
        private String id;
        private String name;
        private String physicalCollectionName;
    }
}
