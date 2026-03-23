package com.agentlego.backend.vectorstore.infrastructure;

import com.agentlego.backend.vectorstore.domain.VectorStoreCollectionBindingRepository;
import com.agentlego.backend.vectorstore.infrastructure.persistence.VectorStoreCollectionBindingDO;
import com.agentlego.backend.vectorstore.infrastructure.persistence.VectorStoreCollectionBindingMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class VectorStoreCollectionBindingRepositoryImpl implements VectorStoreCollectionBindingRepository {

    private final VectorStoreCollectionBindingMapper mapper;

    public VectorStoreCollectionBindingRepositoryImpl(VectorStoreCollectionBindingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertKb(String profileId, String physicalCollectionName, String kbCollectionId) {
        mapper.insertKb(profileId, physicalCollectionName, kbCollectionId);
    }

    @Override
    public void insertMemoryPolicy(String profileId, String physicalCollectionName, String memoryPolicyId) {
        mapper.insertMemoryPolicy(profileId, physicalCollectionName, memoryPolicyId);
    }

    @Override
    public int deleteByMemoryPolicyId(String memoryPolicyId) {
        return mapper.deleteByMemoryPolicyId(memoryPolicyId);
    }

    @Override
    public Optional<CollectionBinding> findByProfileAndPhysicalName(String profileId, String physicalCollectionName) {
        VectorStoreCollectionBindingDO row = mapper.findByProfileAndPhysicalName(profileId, physicalCollectionName);
        if (row == null || row.getKbCollectionId() == null) {
            return Optional.empty();
        }
        return Optional.of(new CollectionBinding(row.getKbCollectionId()));
    }
}
