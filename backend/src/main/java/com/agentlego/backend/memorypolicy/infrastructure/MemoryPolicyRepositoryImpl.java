package com.agentlego.backend.memorypolicy.infrastructure;

import com.agentlego.backend.memorypolicy.domain.MemoryPolicyRepository;
import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryPolicyDO;
import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryPolicyMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MemoryPolicyRepositoryImpl implements MemoryPolicyRepository {

    private final MemoryPolicyMapper mapper;

    public MemoryPolicyRepositoryImpl(MemoryPolicyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<MemoryPolicyDO> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        MemoryPolicyDO row = mapper.findById(id.trim());
        return Optional.ofNullable(row);
    }

    @Override
    public List<MemoryPolicyDO> listAll() {
        List<MemoryPolicyDO> rows = mapper.listAll();
        return rows == null ? List.of() : rows;
    }

    @Override
    public void save(MemoryPolicyDO row) {
        mapper.insert(row);
    }

    @Override
    public void update(MemoryPolicyDO row) {
        mapper.update(row);
    }

    @Override
    public void deleteById(String id) {
        mapper.deleteById(id);
    }

    @Override
    public boolean existsOtherByOwnerScope(String ownerScope, String exceptPolicyId) {
        return mapper.countByOwnerScopeExceptId(ownerScope, exceptPolicyId) > 0;
    }
}
