package com.agentlego.backend.workflow.infrastructure;

import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.workflow.domain.WorkflowRunAggregate;
import com.agentlego.backend.workflow.domain.WorkflowRunRepository;
import com.agentlego.backend.workflow.domain.WorkflowRunStatus;
import com.agentlego.backend.workflow.infrastructure.persistence.WorkflowRunDO;
import com.agentlego.backend.workflow.infrastructure.persistence.WorkflowRunMapper;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class WorkflowRunRepositoryImpl implements WorkflowRunRepository {

    private final WorkflowRunMapper mapper;

    public WorkflowRunRepositoryImpl(WorkflowRunMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String createRun(String workflowId, Map<String, Object> input, String idempotencyKey) {
        String id = com.agentlego.backend.common.SnowflakeIdGenerator.nextId();
        WorkflowRunDO row = new WorkflowRunDO();
        row.setId(id);
        row.setWorkflowId(workflowId);
        row.setStatus(WorkflowRunStatus.PENDING.name());
        row.setIdempotencyKey(idempotencyKey);
        row.setInputJson(JsonMaps.toJson(input));
        mapper.insert(row);
        return id;
    }

    @Override
    public void markRunning(String runId) {
        mapper.updateStatusRunning(runId);
    }

    @Override
    public void markSucceeded(String runId, Map<String, Object> output) {
        mapper.updateStatusSucceeded(runId, JsonMaps.toJson(output));
    }

    @Override
    public void markFailed(String runId, String error) {
        mapper.updateStatusFailed(runId, error);
    }

    @Override
    public WorkflowRunAggregate findById(String runId) {
        WorkflowRunDO row = mapper.findById(runId);
        if (row == null) {
            return null;
        }
        WorkflowRunAggregate agg = new WorkflowRunAggregate();
        agg.setId(row.getId());
        agg.setWorkflowId(row.getWorkflowId());
        agg.setStatus(row.getStatus() == null ? null : WorkflowRunStatus.valueOf(row.getStatus()));
        agg.setError(row.getError());
        agg.setInput(JsonMaps.parseObject(row.getInputJson()));
        agg.setOutput(JsonMaps.parseObject(row.getOutputJson()));
        agg.setStartedAt(row.getStartedAt());
        agg.setFinishedAt(row.getFinishedAt());
        agg.setCreatedAt(row.getCreatedAt());
        return agg;
    }

}

