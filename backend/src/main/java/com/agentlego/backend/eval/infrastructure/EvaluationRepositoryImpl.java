package com.agentlego.backend.eval.infrastructure;

import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.eval.domain.EvaluationAggregate;
import com.agentlego.backend.eval.domain.EvaluationRepository;
import com.agentlego.backend.eval.infrastructure.persistence.EvaluationDO;
import com.agentlego.backend.eval.infrastructure.persistence.EvaluationMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 评测仓库实现（Repository Impl）。
 * <p>
 * 说明：
 * - 该层负责 Aggregate <-> DO 的映射与持久化；
 * - jsonb 字段统一通过 `JsonMaps` 做序列化/反序列化，避免重复样板代码与散落的类型转换。
 */
@Repository
public class EvaluationRepositoryImpl implements EvaluationRepository {

    private final EvaluationMapper mapper;

    public EvaluationRepositoryImpl(EvaluationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String save(EvaluationAggregate aggregate) {
        EvaluationDO row = new EvaluationDO();
        row.setId(aggregate.getId());
        row.setAgentId(aggregate.getAgentId());
        row.setName(aggregate.getName());
        row.setConfigJson(JsonMaps.toJson(aggregate.getConfig()));
        row.setCreatedAt(aggregate.getCreatedAt());
        mapper.insert(row);
        return aggregate.getId();
    }

    @Override
    public Optional<EvaluationAggregate> findById(String id) {
        EvaluationDO row = mapper.findById(id);
        if (row == null) {
            return Optional.empty();
        }
        EvaluationAggregate agg = new EvaluationAggregate();
        agg.setId(row.getId());
        agg.setAgentId(row.getAgentId());
        agg.setName(row.getName());
        agg.setConfig(JsonMaps.parseObject(row.getConfigJson()));
        agg.setCreatedAt(row.getCreatedAt());
        return Optional.of(agg);
    }
}

