package com.agentlego.backend.agent.infrastructure;

import com.agentlego.backend.agent.domain.*;
import com.agentlego.backend.agent.infrastructure.persistence.AgentDO;
import com.agentlego.backend.agent.infrastructure.persistence.AgentMapper;
import com.agentlego.backend.agent.infrastructure.persistence.AgentMemoryPolicyCountRow;
import com.agentlego.backend.agent.infrastructure.persistence.AgentToolRefRow;
import com.agentlego.backend.common.JsonMaps;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

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

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
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
        row.setMemoryPolicyId(blankToNull(aggregate.getMemoryPolicyId()));
        row.setKnowledgeBasePolicyJson(JsonMaps.toJson(aggregate.getKnowledgeBasePolicy()));
        row.setRuntimeKind(aggregate.getRuntimeKind() == null ? "REACT" : aggregate.getRuntimeKind());
        row.setMaxReactIters(aggregate.getMaxReactIters() == null ? 10 : aggregate.getMaxReactIters());
        mapper.insert(row);
        List<String> toolIds = normalizeToolIds(aggregate.getToolIds());
        if (!toolIds.isEmpty()) {
            mapper.insertAgentTools(aggregate.getId(), toolIds);
        }
        return aggregate.getId();
    }

    @Override
    @Transactional
    public void update(AgentAggregate aggregate) {
        AgentDO row = new AgentDO();
        row.setId(aggregate.getId());
        row.setName(aggregate.getName());
        row.setSystemPrompt(aggregate.getSystemPrompt());
        row.setModelId(aggregate.getModelId());
        row.setMemoryPolicyId(blankToNull(aggregate.getMemoryPolicyId()));
        row.setKnowledgeBasePolicyJson(JsonMaps.toJson(aggregate.getKnowledgeBasePolicy()));
        row.setRuntimeKind(aggregate.getRuntimeKind() == null ? "REACT" : aggregate.getRuntimeKind());
        row.setMaxReactIters(aggregate.getMaxReactIters() == null ? 10 : aggregate.getMaxReactIters());
        mapper.updateAgent(row);
        mapper.deleteAgentToolsByAgentId(aggregate.getId());
        List<String> toolIds = normalizeToolIds(aggregate.getToolIds());
        if (!toolIds.isEmpty()) {
            mapper.insertAgentTools(aggregate.getId(), toolIds);
        }
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
    public AgentToolReferenceSnapshot findToolReferencesByToolId(String toolId) {
        List<AgentToolRefRow> rows = mapper.listAgentToolReferencesWithCount(toolId);
        if (rows == null || rows.isEmpty()) {
            return new AgentToolReferenceSnapshot(0, List.of());
        }
        int total = rows.get(0).getTotalCount();
        List<String> ids = rows.stream().map(AgentToolRefRow::getAgentId).toList();
        return new AgentToolReferenceSnapshot(total, ids);
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
    public List<AgentKbPolicyPickerRow> listKbPolicyPickerRows() {
        List<AgentDO> rows = mapper.listKbPolicyPicker();
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<AgentKbPolicyPickerRow> out = new ArrayList<>(rows.size());
        for (AgentDO r : rows) {
            if (r == null || r.getId() == null) {
                continue;
            }
            String json = r.getKnowledgeBasePolicyJson() == null ? "" : r.getKnowledgeBasePolicyJson();
            out.add(new AgentKbPolicyPickerRow(r.getId(), r.getName(), json));
        }
        return out;
    }

    @Override
    public int countByMemoryPolicyId(String policyId) {
        if (policyId == null || policyId.isBlank()) {
            return 0;
        }
        return mapper.countByMemoryPolicyId(policyId.trim());
    }

    @Override
    public Map<String, Integer> countAgentsByMemoryPolicyIds(List<String> policyIds) {
        if (policyIds == null || policyIds.isEmpty()) {
            return Map.of();
        }
        List<String> distinct = policyIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String pid : distinct) {
            out.put(pid, 0);
        }
        List<AgentMemoryPolicyCountRow> rows = mapper.countAgentsByMemoryPolicyIds(distinct);
        if (rows != null) {
            for (AgentMemoryPolicyCountRow r : rows) {
                if (r == null || r.getPolicyId() == null) {
                    continue;
                }
                out.put(r.getPolicyId(), r.getCnt() == null ? 0 : r.getCnt());
            }
        }
        return out;
    }

    @Override
    public List<AgentMemoryPolicyRefRow> listAgentsByMemoryPolicyId(String policyId) {
        if (policyId == null || policyId.isBlank()) {
            return List.of();
        }
        List<AgentDO> rows = mapper.listAgentsByMemoryPolicyId(policyId.trim());
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<AgentMemoryPolicyRefRow> out = new ArrayList<>(rows.size());
        for (AgentDO r : rows) {
            if (r == null || r.getId() == null) {
                continue;
            }
            String nm = r.getName() == null ? "" : r.getName();
            out.add(new AgentMemoryPolicyRefRow(r.getId(), nm));
        }
        return out;
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
        agg.setMemoryPolicyId(row.getMemoryPolicyId());
        agg.setKnowledgeBasePolicy(JsonMaps.parseObject(row.getKnowledgeBasePolicyJson()));
        agg.setRuntimeKind(row.getRuntimeKind());
        agg.setMaxReactIters(row.getMaxReactIters());
        agg.setCreatedAt(row.getCreatedAt());
        return Optional.of(agg);
    }
}

