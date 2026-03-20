package com.agentlego.backend.workflow.infrastructure;

import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.workflow.domain.WorkflowAggregate;
import com.agentlego.backend.workflow.domain.WorkflowRepository;
import com.agentlego.backend.workflow.infrastructure.persistence.WorkflowDO;
import com.agentlego.backend.workflow.infrastructure.persistence.WorkflowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 工作流仓库实现（Repository Impl）。
 * <p>
 * 说明：definitionJson（jsonb/varchar）统一通过 `JsonMaps` 做序列化/反序列化。
 */
@Repository
public class WorkflowRepositoryImpl implements WorkflowRepository {

    private final WorkflowMapper mapper;

    public WorkflowRepositoryImpl(WorkflowMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String save(WorkflowAggregate aggregate) {
        WorkflowDO row = new WorkflowDO();
        row.setId(aggregate.getId());
        row.setName(aggregate.getName());
        row.setDefinitionJson(JsonMaps.toJson(aggregate.getDefinition()));
        mapper.insert(row);
        return aggregate.getId();
    }

    @Override
    public Optional<WorkflowAggregate> findById(String id) {
        WorkflowDO row = mapper.findById(id);
        if (row == null) {
            return Optional.empty();
        }
        WorkflowAggregate agg = new WorkflowAggregate();
        agg.setId(row.getId());
        agg.setName(row.getName());
        agg.setDefinition(JsonMaps.parseObject(row.getDefinitionJson()));
        agg.setCreatedAt(row.getCreatedAt());
        return Optional.of(agg);
    }
}

