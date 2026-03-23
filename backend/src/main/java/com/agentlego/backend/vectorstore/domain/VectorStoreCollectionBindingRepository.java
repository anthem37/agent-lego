package com.agentlego.backend.vectorstore.domain;

import java.util.Optional;

/**
 * 物理集合级绑定：每个 profile × physical_collection_name 至多对应一个知识库集合。
 */
public interface VectorStoreCollectionBindingRepository {

    void insertKb(String profileId, String physicalCollectionName, String kbCollectionId);

    void insertMemoryPolicy(String profileId, String physicalCollectionName, String memoryPolicyId);

    int deleteByMemoryPolicyId(String memoryPolicyId);

    Optional<CollectionBinding> findByProfileAndPhysicalName(String profileId, String physicalCollectionName);

    record CollectionBinding(String kbCollectionId) {
    }
}
