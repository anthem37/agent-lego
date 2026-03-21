package com.agentlego.backend.agent.infrastructure;

import com.agentlego.backend.agent.domain.AgentAggregate;
import com.agentlego.backend.agent.domain.AgentRepository;
import com.agentlego.backend.agent.infrastructure.persistence.AgentDO;
import com.agentlego.backend.agent.infrastructure.persistence.AgentMapper;
import com.agentlego.backend.common.JsonMaps;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class AgentRepositoryImpl implements AgentRepository {

    private final AgentMapper mapper;

    public AgentRepositoryImpl(AgentMapper mapper) {
        this.mapper = mapper;
    }

    private static List<String> normalizeToolIds(List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return List.of();
        }
        return toolIds.stream()
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();
    }

    private static List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Override
    @Transactional
    public String save(AgentAggregate aggregate) {
        AgentDO row = new AgentDO();
        row.setId(aggregate.getId());
        row.setName(aggregate.getName());
        row.setSystemPrompt(aggregate.getSystemPrompt());
        row.setModelId(aggregate.getModelId());
        row.setMemoryPolicyJson(JsonMaps.toJson(aggregate.getMemoryPolicy()));
        row.setKnowledgeBasePolicyJson(JsonMaps.toJson(aggregate.getKnowledgeBasePolicy()));
        mapper.insert(row);
        List<String> toolIds = normalizeToolIds(aggregate.getToolIds());
        if (!toolIds.isEmpty()) {
            mapper.insertAgentTools(aggregate.getId(), toolIds);
        }
        return aggregate.getId();
    }

    @Override
    public int countByModelId(String modelId) {
        return mapper.countByModelId(modelId);
    }

    @Override
    public int countByToolId(String toolId) {
        return mapper.countByToolId(toolId);
    }

    @Override
    public List<String> listAgentIdsByToolId(String toolId) {
        List<String> ids = mapper.listAgentIdsByToolId(toolId);
        return ids == null ? List.of() : ids;
    }

    @Override
    public List<String> listAgentIdsReferencingKbCollection(String collectionId) {
        List<String> ids = mapper.listAgentIdsReferencingKbCollection(collectionId);
        return ids == null ? List.of() : ids;
    }

    @Override
    public void updateKnowledgeBasePolicy(String agentId, Map<String, Object> knowledgeBasePolicy) {
        mapper.updateKnowledgeBasePolicy(agentId, JsonMaps.toJson(knowledgeBasePolicy));
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
        agg.setMemoryPolicy(JsonMaps.parseObject(row.getMemoryPolicyJson()));
        agg.setKnowledgeBasePolicy(JsonMaps.parseObject(row.getKnowledgeBasePolicyJson()));
        agg.setCreatedAt(row.getCreatedAt());
        return Optional.of(agg);
    }
}

