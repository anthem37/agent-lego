package com.agentlego.backend.vectorstore.application.dto;

import lombok.Data;

@Data
public class VectorStoreCollectionSummaryDto {
    private String name;
    /**
     * Milvus：内存加载百分比 0～100；无则 null
     */
    private Integer loadedPercent;
    /**
     * Qdrant：无
     */
    private Boolean queryServiceAvailable;
}
