package com.agentlego.backend.vectorstore.infrastructure.persistence;

import lombok.Data;

import java.time.Instant;

@Data
public class VectorStoreCollectionBindingDO {
    private String profileId;
    private String physicalCollectionName;
    private String kbCollectionId;
    private Instant createdAt;
}
