package com.agentlego.backend.kb.infrastructure;

import com.agentlego.backend.kb.domain.KbCollectionAggregate;
import com.agentlego.backend.kb.domain.KbCollectionRepository;
import com.agentlego.backend.kb.infrastructure.persistence.KbCollectionDO;
import com.agentlego.backend.kb.infrastructure.persistence.KbCollectionMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class KbCollectionRepositoryImpl implements KbCollectionRepository {

    private final KbCollectionMapper mapper;

    public KbCollectionRepositoryImpl(KbCollectionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String save(KbCollectionAggregate aggregate) {
        KbCollectionDO row = new KbCollectionDO();
        row.setId(aggregate.getId());
        row.setName(aggregate.getName());
        row.setDescription(aggregate.getDescription());
        row.setEmbeddingModelId(aggregate.getEmbeddingModelId());
        row.setEmbeddingDims(aggregate.getEmbeddingDims());
        row.setChunkStrategy(aggregate.getChunkStrategy());
        row.setChunkParamsJson(aggregate.getChunkParamsJson());
        row.setCreatedAt(aggregate.getCreatedAt());
        row.setUpdatedAt(aggregate.getUpdatedAt());
        mapper.insert(row);
        return aggregate.getId();
    }

    @Override
    public Optional<KbCollectionAggregate> findById(String id) {
        KbCollectionDO row = mapper.findById(id);
        return row == null ? Optional.empty() : Optional.of(toAgg(row));
    }

    @Override
    public List<KbCollectionAggregate> listAll() {
        List<KbCollectionDO> rows = mapper.listAll();
        if (rows == null) {
            return List.of();
        }
        return rows.stream().map(this::toAgg).toList();
    }

    @Override
    public List<KbCollectionAggregate> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<KbCollectionDO> rows = mapper.findByIds(ids);
        if (rows == null) {
            return List.of();
        }
        return rows.stream().map(this::toAgg).toList();
    }

    @Override
    public void deleteById(String id) {
        mapper.deleteById(id);
    }

    private KbCollectionAggregate toAgg(KbCollectionDO row) {
        KbCollectionAggregate a = new KbCollectionAggregate();
        a.setId(row.getId());
        a.setName(row.getName());
        a.setDescription(row.getDescription());
        a.setEmbeddingModelId(row.getEmbeddingModelId());
        a.setEmbeddingDims(row.getEmbeddingDims() == null ? 1536 : row.getEmbeddingDims());
        a.setChunkStrategy(row.getChunkStrategy() == null ? "FIXED_WINDOW" : row.getChunkStrategy());
        a.setChunkParamsJson(
                row.getChunkParamsJson() == null || row.getChunkParamsJson().isBlank()
                        ? "{\"maxChars\":900,\"overlap\":120}"
                        : row.getChunkParamsJson()
        );
        a.setCreatedAt(row.getCreatedAt());
        a.setUpdatedAt(row.getUpdatedAt());
        return a;
    }
}
