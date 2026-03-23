package com.agentlego.backend.memorypolicy.support;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.api.ApiRequires;
import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.kb.domain.KbVectorStoreKind;
import com.agentlego.backend.kb.vector.KbVectorStoreConfigValidator;
import com.agentlego.backend.memorypolicy.application.dto.CreateMemoryPolicyRequest;
import com.agentlego.backend.memorypolicy.application.dto.UpdateMemoryPolicyRequest;
import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryPolicyDO;
import com.agentlego.backend.vectorstore.domain.VectorStoreCollectionBindingRepository;
import com.agentlego.backend.vectorstore.domain.VectorStoreProfileAggregate;
import com.agentlego.backend.vectorstore.domain.VectorStoreProfileRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 记忆策略向量配置：与知识库集合一致，合并 {@link VectorStoreProfileAggregate} 与请求中的 {@code collectionName} 覆盖。
 */
@Service
public class MemoryPolicyVectorConfigService {

    private final VectorStoreProfileRepository vectorStoreProfileRepository;
    private final KbVectorStoreConfigValidator vectorStoreConfigValidator;
    private final ObjectMapper objectMapper;
    private final VectorStoreCollectionBindingRepository collectionBindingRepository;

    public MemoryPolicyVectorConfigService(
            VectorStoreProfileRepository vectorStoreProfileRepository,
            KbVectorStoreConfigValidator vectorStoreConfigValidator,
            ObjectMapper objectMapper,
            VectorStoreCollectionBindingRepository collectionBindingRepository
    ) {
        this.vectorStoreProfileRepository = vectorStoreProfileRepository;
        this.vectorStoreConfigValidator = vectorStoreConfigValidator;
        this.objectMapper = objectMapper;
        this.collectionBindingRepository = collectionBindingRepository;
    }

    /**
     * 默认物理集合名：与知识库隔离，前缀 mem_pol_。
     */
    public static String defaultMemoryCollectionName(String policyId) {
        String pid = policyId == null ? "p" : policyId.trim();
        String safe = pid.replaceAll("[^a-zA-Z0-9_]", "_");
        return "mem_pol_" + safe;
    }

    public void applyVectorFieldsForCreate(CreateMemoryPolicyRequest req, MemoryPolicyDO row) {
        applyVectorFields(req == null ? null : req.getVectorStoreProfileId(),
                req == null ? null : req.getVectorStoreConfig(),
                req == null ? null : req.getVectorMinScore(),
                row);
    }

    public void applyVectorFieldsForUpdate(UpdateMemoryPolicyRequest req, MemoryPolicyDO row) {
        if (Boolean.TRUE.equals(req.getClearVectorLink())) {
            row.setVectorStoreProfileId(null);
            row.setVectorStoreConfigJson("{}");
            row.setVectorMinScore(0.15d);
            return;
        }
        if (req.getVectorMinScore() != null && req.getVectorStoreProfileId() == null && req.getVectorStoreConfig() == null) {
            double min = Math.min(1.0d, Math.max(0.0d, req.getVectorMinScore()));
            row.setVectorMinScore(min);
            return;
        }
        if (req.getVectorStoreProfileId() != null
                || req.getVectorStoreConfig() != null
                || req.getVectorMinScore() != null) {
            applyVectorFields(
                    req.getVectorStoreProfileId() != null ? req.getVectorStoreProfileId() : row.getVectorStoreProfileId(),
                    req.getVectorStoreConfig() != null ? req.getVectorStoreConfig() : JsonMaps.parseObject(row.getVectorStoreConfigJson()),
                    req.getVectorMinScore() != null ? req.getVectorMinScore() : row.getVectorMinScore(),
                    row
            );
        }
    }

    private void applyVectorFields(String profileIdReq, Map<String, Object> configOverride, Double vectorMinScoreReq, MemoryPolicyDO row) {
        String mode = row.getRetrievalMode() == null ? "" : row.getRetrievalMode().trim().toUpperCase();
        if (!MemoryPolicySemantic.RETRIEVAL_VECTOR.equals(mode) && !MemoryPolicySemantic.RETRIEVAL_HYBRID.equals(mode)) {
            row.setVectorStoreProfileId(null);
            row.setVectorStoreConfigJson("{}");
            row.setVectorMinScore(0.15d);
            return;
        }
        ApiRequires.nonBlank(profileIdReq, "vectorStoreProfileId");
        String profileId = profileIdReq.trim();
        VectorStoreProfileAggregate prof = vectorStoreProfileRepository.findById(profileId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "vectorStoreProfile 未找到", HttpStatus.NOT_FOUND));

        if (configOverride != null) {
            for (String k : configOverride.keySet()) {
                if (!"collectionName".equals(k)) {
                    throw new ApiException(
                            "VALIDATION_ERROR",
                            "记忆策略向量配置仅允许在 vectorStoreConfig 中覆盖 collectionName（物理集合名）",
                            HttpStatus.BAD_REQUEST
                    );
                }
            }
        }
        Map<String, Object> base = JsonMaps.parseObject(prof.getVectorStoreConfigJson());
        Map<String, Object> override = new HashMap<>();
        if (configOverride != null && configOverride.get("collectionName") != null) {
            override.put("collectionName", configOverride.get("collectionName"));
        }
        Map<String, Object> merged = JsonMaps.shallowMerge(base, override);
        String collectionName = JsonMaps.getString(merged, "collectionName", "");
        if (collectionName.isBlank()) {
            collectionName = defaultMemoryCollectionName(row.getId());
            merged.put("collectionName", collectionName);
        }
        KbVectorStoreKind storeKind = KbVectorStoreKind.fromApi(prof.getVectorStoreKind());
        vectorStoreConfigValidator.validate(storeKind, merged);
        String vectorStoreConfigJson;
        try {
            vectorStoreConfigJson = objectMapper.writeValueAsString(merged);
        } catch (JsonProcessingException e) {
            throw new ApiException("VALIDATION_ERROR", "vectorStoreConfig 无法序列化", HttpStatus.BAD_REQUEST);
        }
        row.setVectorStoreProfileId(profileId);
        row.setVectorStoreConfigJson(vectorStoreConfigJson);
        double min = vectorMinScoreReq == null ? 0.15d : Math.min(1.0d, Math.max(0.0d, vectorMinScoreReq));
        row.setVectorMinScore(min);
    }

    /**
     * 创建/更新策略落库后：独占绑定 profile×物理集合。
     */
    public void syncBindingAfterSave(MemoryPolicyDO row) {
        collectionBindingRepository.deleteByMemoryPolicyId(row.getId());
        if (!MemoryPolicySemantic.isVectorRetrieval(row.getRetrievalMode())) {
            return;
        }
        if (row.getVectorStoreProfileId() == null || row.getVectorStoreProfileId().isBlank()) {
            return;
        }
        String cn = JsonMaps.getString(JsonMaps.parseObject(row.getVectorStoreConfigJson()), "collectionName", "");
        if (cn.isBlank()) {
            return;
        }
        try {
            collectionBindingRepository.insertMemoryPolicy(row.getVectorStoreProfileId().trim(), cn.trim(), row.getId());
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(
                    "VECTOR_COLLECTION_CONFLICT",
                    "该物理 collection 已被占用或重复绑定",
                    HttpStatus.CONFLICT
            );
        }
    }
}
