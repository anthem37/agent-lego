package com.agentlego.backend.workflow.infrastructure;

import com.agentlego.backend.workflow.domain.WorkflowRunAggregate;
import com.agentlego.backend.workflow.domain.WorkflowRunRepository;
import com.agentlego.backend.workflow.domain.WorkflowRunStatus;
import com.agentlego.backend.workflow.infrastructure.persistence.WorkflowRunDO;
import com.agentlego.backend.workflow.infrastructure.persistence.WorkflowRunMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class WorkflowRunRepositoryImpl implements WorkflowRunRepository {

    private final WorkflowRunMapper mapper;
    private final ObjectMapper objectMapper;

    public WorkflowRunRepositoryImpl(WorkflowRunMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String createRun(String workflowId, Map<String, Object> input, String idempotencyKey) {
        String id = com.agentlego.backend.common.SnowflakeIdGenerator.nextId();
        WorkflowRunDO row = new WorkflowRunDO();
        row.setId(id);
        row.setWorkflowId(workflowId);
        row.setStatus(WorkflowRunStatus.PENDING.name());
        row.setIdempotencyKey(idempotencyKey);
        row.setInputJson(toJson(input));
        mapper.insert(row);
        return id;
    }

    @Override
    public void markRunning(String runId) {
        mapper.updateStatusRunning(runId);
    }

    @Override
    public void markSucceeded(String runId, Map<String, Object> output) {
        mapper.updateStatusSucceeded(runId, toJson(output));
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
        agg.setInput(fromJson(row.getInputJson()));
        agg.setOutput(fromJson(row.getOutputJson()));
        agg.setStartedAt(row.getStartedAt());
        agg.setFinishedAt(row.getFinishedAt());
        agg.setCreatedAt(row.getCreatedAt());
        return agg;
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map == null ? Map.of() : map);
        } catch (Exception e) {
            throw new RuntimeException("serialize workflow run json failed", e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("deserialize workflow run json failed", e);
        }
    }
}

