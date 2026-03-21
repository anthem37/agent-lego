package com.agentlego.backend.eval.infrastructure;

import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.eval.domain.EvaluationRunAggregate;
import com.agentlego.backend.eval.domain.EvaluationRunRepository;
import com.agentlego.backend.eval.domain.EvaluationRunStatus;
import com.agentlego.backend.eval.infrastructure.persistence.EvaluationRunDO;
import com.agentlego.backend.eval.infrastructure.persistence.EvaluationRunMapper;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

@Repository
public class EvaluationRunRepositoryImpl implements EvaluationRunRepository {

    private final EvaluationRunMapper mapper;

    public EvaluationRunRepositoryImpl(EvaluationRunMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String createRun(String evaluationId, Map<String, Object> input) {
        String id = SnowflakeIdGenerator.nextId();
        EvaluationRunDO row = new EvaluationRunDO();
        row.setId(id);
        row.setEvaluationId(evaluationId);
        row.setStatus(EvaluationRunStatus.PENDING.name());
        row.setInputJson(JsonMaps.toJson(input));
        mapper.insert(row);
        return id;
    }

    @Override
    public void markRunning(String runId) {
        mapper.updateStatusRunning(runId);
    }

    @Override
    public void markSucceeded(String runId, Map<String, Object> metrics, Map<String, Object> trace) {
        mapper.updateStatusSucceeded(runId, JsonMaps.toJson(metrics), JsonMaps.toJson(trace));
    }

    @Override
    public void markFailed(String runId, String error) {
        mapper.updateStatusFailed(runId, error);
    }

    @Override
    public Optional<EvaluationRunAggregate> findById(String runId) {
        EvaluationRunDO row = mapper.findById(runId);
        if (row == null) {
            return Optional.empty();
        }
        EvaluationRunAggregate agg = new EvaluationRunAggregate();
        agg.setId(row.getId());
        agg.setEvaluationId(row.getEvaluationId());
        agg.setStatus(row.getStatus() == null ? null : EvaluationRunStatus.valueOf(row.getStatus()));
        agg.setError(row.getError());
        agg.setInput(JsonMaps.parseObject(row.getInputJson()));
        agg.setMetrics(JsonMaps.parseObject(row.getMetricsJson()));
        agg.setTrace(JsonMaps.parseObject(row.getTraceJson()));
        agg.setStartedAt(row.getStartedAt());
        agg.setFinishedAt(row.getFinishedAt());
        agg.setCreatedAt(row.getCreatedAt());
        return Optional.of(agg);
    }

}

