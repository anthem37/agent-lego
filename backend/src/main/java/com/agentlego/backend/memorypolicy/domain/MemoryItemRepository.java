package com.agentlego.backend.memorypolicy.domain;

import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryItemDO;

import java.util.List;

public interface MemoryItemRepository {

    List<MemoryItemDO> searchByKeyword(String policyId, String queryText, int limit);

    void insert(MemoryItemDO row);

    String findIdByPolicyIdAndContent(String policyId, String content);

    void touchUpdatedAt(String id);

    int deleteByPolicyIdAndItemId(String policyId, String itemId);
}
