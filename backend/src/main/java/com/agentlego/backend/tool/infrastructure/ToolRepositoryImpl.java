package com.agentlego.backend.tool.infrastructure;

import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.domain.ToolRepository;
import com.agentlego.backend.tool.domain.ToolType;
import com.agentlego.backend.tool.infrastructure.persistence.ToolDO;
import com.agentlego.backend.tool.infrastructure.persistence.ToolMapper;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 工具仓库实现（Repository Impl）。
 * <p>
 * 说明：definitionJson 统一通过 `JsonMaps` 做序列化/反序列化。
 */
@Repository
public class ToolRepositoryImpl implements ToolRepository {

    private final ToolMapper mapper;

    public ToolRepositoryImpl(ToolMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String save(ToolAggregate aggregate) {
        ToolDO toolDO = new ToolDO();
        toolDO.setId(aggregate.getId());
        toolDO.setToolType(aggregate.getToolType().name());
        toolDO.setName(aggregate.getName());
        toolDO.setDefinitionJson(JsonMaps.toJson(aggregate.getDefinition()));
        mapper.insert(toolDO);
        return aggregate.getId();
    }

    @Override
    public void update(ToolAggregate aggregate) {
        ToolDO toolDO = new ToolDO();
        toolDO.setId(aggregate.getId());
        toolDO.setToolType(aggregate.getToolType().name());
        toolDO.setName(aggregate.getName());
        toolDO.setDefinitionJson(JsonMaps.toJson(aggregate.getDefinition()));
        mapper.update(toolDO);
    }

    @Override
    public int deleteById(String id) {
        return mapper.deleteById(id);
    }

    @Override
    public boolean existsByToolTypeAndName(ToolType toolType, String name) {
        return mapper.countByTypeAndName(toolType.name(), name) > 0;
    }

    @Override
    public boolean existsByToolTypeAndNameExcludingId(ToolType toolType, String name, String excludeId) {
        return mapper.countByTypeAndNameExcludeId(toolType.name(), name, excludeId) > 0;
    }

    @Override
    public boolean existsOtherWithNameIgnoreCase(String name, String excludeId) {
        return mapper.countByNameIgnoreCaseExcluding(name, excludeId) > 0;
    }

    @Override
    public Optional<ToolAggregate> findById(String id) {
        ToolDO toolDO = mapper.findById(id);
        if (toolDO == null) {
            return Optional.empty();
        }
        ToolAggregate agg = new ToolAggregate();
        agg.setId(toolDO.getId());
        agg.setToolType(ToolType.valueOf(toolDO.getToolType()));
        agg.setName(toolDO.getName());
        agg.setDefinition(JsonMaps.parseObject(toolDO.getDefinitionJson()));
        agg.setCreatedAt(toolDO.getCreatedAt());
        return Optional.of(agg);
    }

    @Override
    public List<ToolAggregate> findAll() {
        List<ToolDO> rows = mapper.findAll();
        if (rows == null) {
            return Collections.emptyList();
        }
        return rows.stream().map(this::toAggregate).toList();
    }

    @Override
    public long countByQuery(String q, String toolType) {
        return mapper.countByQuery(q, toolType);
    }

    @Override
    public List<ToolAggregate> findPageByQuery(String q, String toolType, long offset, int limit) {
        List<ToolDO> rows = mapper.findPageByQuery(q, toolType, offset, limit);
        if (rows == null) {
            return Collections.emptyList();
        }
        return rows.stream().map(this::toAggregate).toList();
    }

    private ToolAggregate toAggregate(ToolDO r) {
        ToolAggregate agg = new ToolAggregate();
        agg.setId(r.getId());
        agg.setToolType(ToolType.valueOf(r.getToolType()));
        agg.setName(r.getName());
        agg.setDefinition(JsonMaps.parseObject(r.getDefinitionJson()));
        agg.setCreatedAt(r.getCreatedAt());
        return agg;
    }
}

