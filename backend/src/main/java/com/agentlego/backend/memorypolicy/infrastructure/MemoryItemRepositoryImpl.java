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
    public List<MemoryItemDO> searchByKeyword(
            String policyId,
            String queryText,
            int limit,
            String memoryNamespace,
            String strategyKind,
            boolean orderByTrgm
    ) {
        List<MemoryItemDO> rows = mapper.searchByKeyword(
                policyId,
                queryText == null ? "" : queryText,
                limit,
                memoryNamespace,
                strategyKind,
                orderByTrgm
        );
        return rows == null ? List.of() : rows;
    }

    @Override
    public List<MemoryItemDO> findByIds(String policyId, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<MemoryItemDO> rows = mapper.findByIds(policyId, ids);
        return rows == null ? List.of() : rows;
    }

    @Override
    public List<MemoryItemDO> listByPolicyId(String policyId) {
        if (policyId == null || policyId.isBlank()) {
            return List.of();
        }
        List<MemoryItemDO> rows = mapper.listByPolicyId(policyId.trim());
        return rows == null ? List.of() : rows;
    }

    @Override
    public void insert(MemoryItemDO row) {
        mapper.insert(row);
    }

    @Override
    public String findIdByPolicyIdAndContent(String policyId, String content, String memoryNamespace, String strategyKind) {
        return mapper.findIdByPolicyIdAndContent(policyId, content, memoryNamespace, strategyKind);
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
