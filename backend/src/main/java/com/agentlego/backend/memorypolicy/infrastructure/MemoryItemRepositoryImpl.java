package com.agentlego.backend.memorypolicy.infrastructure;

import com.agentlego.backend.memorypolicy.domain.MemoryItemRepository;
import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryItemDO;
import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryItemMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MemoryItemRepositoryImpl implements MemoryItemRepository {

    private final MemoryItemMapper mapper;

    public MemoryItemRepositoryImpl(MemoryItemMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<MemoryItemDO> searchByKeyword(String policyId, String queryText, int limit) {
        List<MemoryItemDO> rows = mapper.searchByKeyword(policyId, queryText == null ? "" : queryText, limit);
        return rows == null ? List.of() : rows;
    }

    @Override
    public void insert(MemoryItemDO row) {
        mapper.insert(row);
    }

    @Override
    public String findIdByPolicyIdAndContent(String policyId, String content) {
        return mapper.findIdByPolicyIdAndContent(policyId, content);
    }

    @Override
    public void touchUpdatedAt(String id) {
        mapper.touchUpdatedAt(id);
    }

    @Override
    public int deleteByPolicyIdAndItemId(String policyId, String itemId) {
        return mapper.deleteByPolicyIdAndItemId(policyId, itemId);
    }
}
