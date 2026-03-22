package com.agentlego.backend.memorypolicy.domain;

import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryPolicyDO;

import java.util.List;
import java.util.Optional;

public interface MemoryPolicyRepository {

    Optional<MemoryPolicyDO> findById(String id);

    List<MemoryPolicyDO> listAll();

    void save(MemoryPolicyDO row);

    void update(MemoryPolicyDO row);

    void deleteById(String id);

    boolean existsOtherByOwnerScope(String ownerScope, String exceptPolicyId);
}
