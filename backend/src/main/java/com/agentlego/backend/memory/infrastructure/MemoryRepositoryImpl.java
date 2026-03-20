package com.agentlego.backend.memory.infrastructure;

import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.memory.domain.MemoryItemAggregate;
import com.agentlego.backend.memory.domain.MemoryRepository;
import com.agentlego.backend.memory.infrastructure.persistence.MemoryItemDO;
import com.agentlego.backend.memory.infrastructure.persistence.MemoryItemMapper;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/**
 * 记忆仓库实现（Repository Impl）。
 * <p>
 * 说明：metadataJson 统一通过 `JsonMaps` 做序列化/反序列化。
 */
@Repository
public class MemoryRepositoryImpl implements MemoryRepository {

    private final MemoryItemMapper mapper;

    public MemoryRepositoryImpl(MemoryItemMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String save(MemoryItemAggregate aggregate) {
        MemoryItemDO item = new MemoryItemDO();
        item.setId(aggregate.getId());
        item.setOwnerScope(aggregate.getOwnerScope());
        item.setContent(aggregate.getContent());
        item.setMetadataJson(JsonMaps.toJson(aggregate.getMetadata()));
        mapper.insert(item);
        return aggregate.getId();
    }

    @Override
    public List<MemoryItemAggregate> search(String ownerScope, String queryText, int topK) {
        String q = (queryText == null || queryText.isBlank()) ? "" : queryText;
        List<MemoryItemDO> rows = mapper.search(ownerScope, q, topK);
        if (rows == null) {
            return Collections.emptyList();
        }
        return rows.stream().map(this::toAggregate).toList();
    }

    private MemoryItemAggregate toAggregate(MemoryItemDO row) {
        MemoryItemAggregate agg = new MemoryItemAggregate();
        agg.setId(row.getId());
        agg.setOwnerScope(row.getOwnerScope());
        agg.setContent(row.getContent());
        agg.setMetadata(JsonMaps.parseObject(row.getMetadataJson()));
        agg.setCreatedAt(row.getCreatedAt());
        return agg;
    }
}

