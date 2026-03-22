package com.agentlego.backend.vectorstore.infrastructure;

import com.agentlego.backend.vectorstore.domain.VectorStoreProfileAggregate;
import com.agentlego.backend.vectorstore.domain.VectorStoreProfileRepository;
import com.agentlego.backend.vectorstore.infrastructure.persistence.VectorStoreProfileDO;
import com.agentlego.backend.vectorstore.infrastructure.persistence.VectorStoreProfileMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class VectorStoreProfileRepositoryImpl implements VectorStoreProfileRepository {

    private final VectorStoreProfileMapper mapper;

    public VectorStoreProfileRepositoryImpl(VectorStoreProfileMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String save(VectorStoreProfileAggregate aggregate) {
        VectorStoreProfileDO row = new VectorStoreProfileDO();
        row.setId(aggregate.getId());
        row.setName(aggregate.getName());
        row.setVectorStoreKind(aggregate.getVectorStoreKind());
        row.setVectorStoreConfigJson(aggregate.getVectorStoreConfigJson());
        row.setEmbeddingModelId(aggregate.getEmbeddingModelId());
        row.setEmbeddingDims(aggregate.getEmbeddingDims());
        row.setCreatedAt(aggregate.getCreatedAt());
        row.setUpdatedAt(aggregate.getUpdatedAt());
        mapper.insert(row);
        return aggregate.getId();
    }

    @Override
    public Optional<VectorStoreProfileAggregate> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        VectorStoreProfileDO row = mapper.findById(id);
        return row == null ? Optional.empty() : Optional.of(toAgg(row));
    }

    @Override
    public List<VectorStoreProfileAggregate> listAll() {
        List<VectorStoreProfileDO> rows = mapper.listAll();
        if (rows == null) {
            return List.of();
        }
        return rows.stream().map(this::toAgg).toList();
    }

    @Override
    public int update(VectorStoreProfileAggregate aggregate) {
        VectorStoreProfileDO row = new VectorStoreProfileDO();
        row.setId(aggregate.getId());
        row.setName(aggregate.getName());
        row.setVectorStoreKind(aggregate.getVectorStoreKind());
        row.setVectorStoreConfigJson(aggregate.getVectorStoreConfigJson());
        row.setEmbeddingModelId(aggregate.getEmbeddingModelId());
        row.setEmbeddingDims(aggregate.getEmbeddingDims());
        row.setUpdatedAt(aggregate.getUpdatedAt());
        return mapper.update(row);
    }

    @Override
    public int deleteById(String id) {
        return mapper.deleteById(id);
    }

    private VectorStoreProfileAggregate toAgg(VectorStoreProfileDO row) {
        VectorStoreProfileAggregate a = new VectorStoreProfileAggregate();
        a.setId(row.getId());
        a.setName(row.getName());
        a.setVectorStoreKind(row.getVectorStoreKind());
        a.setVectorStoreConfigJson(
                row.getVectorStoreConfigJson() == null || row.getVectorStoreConfigJson().isBlank()
                        ? "{}"
                        : row.getVectorStoreConfigJson()
        );
        a.setEmbeddingModelId(row.getEmbeddingModelId());
        a.setEmbeddingDims(row.getEmbeddingDims() == null ? 0 : row.getEmbeddingDims());
        a.setCreatedAt(row.getCreatedAt());
        a.setUpdatedAt(row.getUpdatedAt());
        return a;
    }
}
