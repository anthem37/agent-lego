package com.agentlego.backend.memorypolicy.application.service;

import com.agentlego.backend.agent.application.dto.AgentRefDto;
import com.agentlego.backend.agent.domain.AgentRepository;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.memorypolicy.application.dto.*;
import com.agentlego.backend.memorypolicy.domain.MemoryItemRepository;
import com.agentlego.backend.memorypolicy.domain.MemoryPolicyRepository;
import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryItemDO;
import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryPolicyDO;
import com.agentlego.backend.memorypolicy.support.MemoryPolicySemantic;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MemoryPolicyApplicationService {

    private final MemoryPolicyRepository memoryPolicyRepository;
    private final MemoryItemRepository memoryItemRepository;
    private final AgentRepository agentRepository;

    public MemoryPolicyApplicationService(
            MemoryPolicyRepository memoryPolicyRepository,
            MemoryItemRepository memoryItemRepository,
            AgentRepository agentRepository
    ) {
        this.memoryPolicyRepository = memoryPolicyRepository;
        this.memoryItemRepository = memoryItemRepository;
        this.agentRepository = agentRepository;
    }

    private static void requireNonEmptyOwnerScope(String scope) {
        if (scope.isEmpty()) {
            throw new ApiException("INVALID_MEMORY_POLICY", "ownerScope 不能为空", HttpStatus.BAD_REQUEST);
        }
    }

    private static int clampTopK(Integer v) {
        if (v == null) {
            return 5;
        }
        return Math.min(Math.max(v, 1), 32);
    }

    private static String normalizeWriteBackDup(String v) {
        if (v != null && "upsert".equalsIgnoreCase(v.trim())) {
            return "upsert";
        }
        return "skip";
    }

    private static MemoryPolicyDto toPolicyDto(MemoryPolicyDO r) {
        MemoryPolicyDto dto = new MemoryPolicyDto();
        dto.setId(r.getId());
        dto.setName(r.getName());
        dto.setDescription(r.getDescription());
        dto.setOwnerScope(r.getOwnerScope());
        dto.setStrategyKind(r.getStrategyKind());
        dto.setScopeKind(r.getScopeKind());
        dto.setRetrievalMode(r.getRetrievalMode());
        dto.setTopK(r.getTopK());
        dto.setWriteMode(r.getWriteMode());
        dto.setWriteBackOnDuplicate(r.getWriteBackOnDuplicate());
        dto.setCreatedAt(r.getCreatedAt());
        dto.setUpdatedAt(r.getUpdatedAt());
        return dto;
    }

    private static MemoryItemDto toItemDto(MemoryItemDO row) {
        MemoryItemDto dto = new MemoryItemDto();
        dto.setId(row.getId());
        dto.setPolicyId(row.getPolicyId());
        dto.setContent(row.getContent());
        dto.setMetadata(JsonMaps.parseObject(row.getMetadataJson()));
        dto.setCreatedAt(row.getCreatedAt());
        dto.setUpdatedAt(row.getUpdatedAt());
        return dto;
    }

    public List<MemoryPolicyDto> listPolicies() {
        List<MemoryPolicyDO> rows = memoryPolicyRepository.listAll();
        List<String> ids = rows.stream().map(MemoryPolicyDO::getId).toList();
        Map<String, Integer> counts = agentRepository.countAgentsByMemoryPolicyIds(ids);
        List<MemoryPolicyDto> out = new ArrayList<>(rows.size());
        for (MemoryPolicyDO r : rows) {
            MemoryPolicyDto dto = toPolicyDto(r);
            dto.setReferencingAgentCount(counts.getOrDefault(r.getId(), 0));
            out.add(dto);
        }
        return out;
    }

    public MemoryPolicyDto getPolicy(String id) {
        MemoryPolicyDO row = requirePolicy(id);
        MemoryPolicyDto dto = toPolicyDto(row);
        dto.setReferencingAgentCount(agentRepository.countByMemoryPolicyId(id));
        return dto;
    }

    public List<AgentRefDto> listReferencingAgents(String policyId) {
        requirePolicy(policyId);
        return agentRepository.listAgentsByMemoryPolicyId(policyId).stream().map(r -> {
            AgentRefDto d = new AgentRefDto();
            d.setId(r.id());
            d.setName(r.name());
            return d;
        }).toList();
    }

    public String createPolicy(CreateMemoryPolicyRequest req) {
        String scope = req.getOwnerScope() == null ? "" : req.getOwnerScope().trim();
        requireNonEmptyOwnerScope(scope);
        assertOwnerScopeUnique(scope, null);
        int topK = clampTopK(req.getTopK());
        String wdup = normalizeWriteBackDup(req.getWriteBackOnDuplicate());

        MemoryPolicyDO row = new MemoryPolicyDO();
        row.setId(SnowflakeIdGenerator.nextId());
        row.setName(req.getName().trim());
        row.setDescription(req.getDescription() == null ? null : req.getDescription().trim());
        row.setOwnerScope(scope);
        row.setStrategyKind(MemoryPolicySemantic.normalizeStrategyKind(req.getStrategyKind()));
        row.setScopeKind(MemoryPolicySemantic.normalizeScopeKind(req.getScopeKind()));
        row.setRetrievalMode(MemoryPolicySemantic.normalizeRetrievalMode(req.getRetrievalMode()));
        row.setTopK(topK);
        row.setWriteMode(MemoryPolicySemantic.normalizeWriteMode(req.getWriteMode()));
        row.setWriteBackOnDuplicate(wdup);
        Instant now = Instant.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        memoryPolicyRepository.save(row);
        return row.getId();
    }

    public void updatePolicy(String id, UpdateMemoryPolicyRequest req) {
        MemoryPolicyDO row = requirePolicy(id);
        String scope = req.getOwnerScope() == null ? "" : req.getOwnerScope().trim();
        requireNonEmptyOwnerScope(scope);
        assertOwnerScopeUnique(scope, id);
        row.setName(req.getName().trim());
        row.setDescription(req.getDescription() == null ? null : req.getDescription().trim());
        row.setOwnerScope(scope);
        if (req.getStrategyKind() != null) {
            row.setStrategyKind(MemoryPolicySemantic.normalizeStrategyKind(req.getStrategyKind()));
        }
        if (req.getScopeKind() != null) {
            row.setScopeKind(MemoryPolicySemantic.normalizeScopeKind(req.getScopeKind()));
        }
        if (req.getRetrievalMode() != null) {
            row.setRetrievalMode(MemoryPolicySemantic.normalizeRetrievalMode(req.getRetrievalMode()));
        }
        if (req.getTopK() != null) {
            row.setTopK(clampTopK(req.getTopK()));
        }
        if (req.getWriteMode() != null) {
            row.setWriteMode(MemoryPolicySemantic.normalizeWriteMode(req.getWriteMode()));
        }
        if (req.getWriteBackOnDuplicate() != null) {
            row.setWriteBackOnDuplicate(normalizeWriteBackDup(req.getWriteBackOnDuplicate()));
        }
        row.setUpdatedAt(Instant.now());
        memoryPolicyRepository.update(row);
    }

    public void deletePolicy(String id) {
        requirePolicy(id);
        int n = agentRepository.countByMemoryPolicyId(id);
        if (n > 0) {
            throw new ApiException(
                    "MEMORY_POLICY_IN_USE",
                    "仍有 " + n + " 个智能体绑定该策略，请先在智能体详情中改绑或解绑后再删除。",
                    HttpStatus.CONFLICT
            );
        }
        memoryPolicyRepository.deleteById(id);
    }

    public List<MemoryItemDto> listItems(String policyId, String q, Integer limit) {
        requirePolicy(policyId);
        int lim = limit == null ? 20 : Math.min(Math.max(limit, 1), 100);
        List<MemoryItemDO> rows = memoryItemRepository.searchByKeyword(policyId, q == null ? "" : q, lim);
        List<MemoryItemDto> out = new ArrayList<>(rows.size());
        for (MemoryItemDO row : rows) {
            out.add(toItemDto(row));
        }
        return out;
    }

    public String createItem(String policyId, CreateMemoryItemRequest req) {
        requirePolicy(policyId);
        String content = req.getContent() == null ? "" : req.getContent().trim();
        if (content.isEmpty()) {
            throw new ApiException("INVALID_MEMORY_ITEM", "content 不能为空", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> meta = req.getMetadata() == null ? Map.of() : req.getMetadata();
        MemoryItemDO row = new MemoryItemDO();
        row.setId(SnowflakeIdGenerator.nextId());
        row.setPolicyId(policyId);
        row.setContent(content);
        row.setMetadataJson(JsonMaps.toJson(meta));
        row.setCreatedAt(Instant.now());
        row.setUpdatedAt(null);
        memoryItemRepository.insert(row);
        return row.getId();
    }

    public void deleteItem(String policyId, String itemId) {
        requirePolicy(policyId);
        if (itemId == null || itemId.isBlank()) {
            throw new ApiException("INVALID_MEMORY_ITEM", "id 无效", HttpStatus.BAD_REQUEST);
        }
        int n = memoryItemRepository.deleteByPolicyIdAndItemId(policyId, itemId.trim());
        if (n == 0) {
            throw new ApiException("NOT_FOUND", "记忆条目未找到或不属于该策略", HttpStatus.NOT_FOUND);
        }
    }

    private MemoryPolicyDO requirePolicy(String id) {
        return memoryPolicyRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "记忆策略未找到", HttpStatus.NOT_FOUND));
    }

    private void assertOwnerScopeUnique(String scope, String excludePolicyId) {
        if (memoryPolicyRepository.existsOtherByOwnerScope(scope, excludePolicyId)) {
            throw new ApiException("DUPLICATE_OWNER_SCOPE", "ownerScope 已存在", HttpStatus.CONFLICT);
        }
    }
}
