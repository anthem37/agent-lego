package com.agentlego.backend.agent.infrastructure;

import com.agentlego.backend.agent.domain.AgentAggregate;
import com.agentlego.backend.agent.domain.AgentRepository;
import com.agentlego.backend.agent.infrastructure.persistence.AgentDO;
import com.agentlego.backend.agent.infrastructure.persistence.AgentMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class AgentRepositoryImpl implements AgentRepository {

    private final AgentMapper mapper;
    private final ObjectMapper objectMapper;

    public AgentRepositoryImpl(AgentMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String save(AgentAggregate aggregate) {
        AgentDO row = new AgentDO();
        row.setId(aggregate.getId());
        row.setName(aggregate.getName());
        row.setSystemPrompt(aggregate.getSystemPrompt());
        row.setModelId(aggregate.getModelId());
        row.setToolIdsCsv(String.join(",", aggregate.getToolIds() == null ? List.of() : aggregate.getToolIds()));
        row.setMemoryPolicyJson(toJson(aggregate.getMemoryPolicy()));
        row.setKnowledgeBasePolicyJson(toJson(aggregate.getKnowledgeBasePolicy()));
        mapper.insert(row);
        return aggregate.getId();
    }

    @Override
    public int countByModelId(String modelId) {
        return mapper.countByModelId(modelId);
    }

    @Override
    public Optional<AgentAggregate> findById(String id) {
        AgentDO row = mapper.findById(id);
        if (row == null) {
            return Optional.empty();
        }
        AgentAggregate agg = new AgentAggregate();
        agg.setId(row.getId());
        agg.setName(row.getName());
        agg.setSystemPrompt(row.getSystemPrompt());
        agg.setModelId(row.getModelId());
        agg.setToolIds(parseCsv(row.getToolIdsCsv()));
        agg.setMemoryPolicy(fromJson(row.getMemoryPolicyJson()));
        agg.setKnowledgeBasePolicy(fromJson(row.getKnowledgeBasePolicyJson()));
        agg.setCreatedAt(row.getCreatedAt());
        return Optional.of(agg);
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map == null ? Map.of() : map);
        } catch (Exception e) {
            throw new RuntimeException("serialize agent policy failed", e);
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
            throw new RuntimeException("deserialize agent policy failed", e);
        }
    }

    private List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        String[] parts = csv.split(",");
        return List.of(parts);
    }
}

