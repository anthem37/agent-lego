package com.agentlego.backend.kb.application.service;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.api.ApiRequires;
import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.kb.application.dto.CreateKbCollectionRequest;
import com.agentlego.backend.kb.application.dto.KbCollectionDto;
import com.agentlego.backend.kb.application.mapper.KbDtoMapper;
import com.agentlego.backend.kb.domain.KbChunkStrategyKind;
import com.agentlego.backend.kb.domain.KbCollectionAggregate;
import com.agentlego.backend.kb.domain.KbCollectionRepository;
import com.agentlego.backend.kb.domain.KbVectorStoreKind;
import com.agentlego.backend.kb.support.KbChunkExecutor;
import com.agentlego.backend.kb.support.KbLimits;
import com.agentlego.backend.kb.vector.KbVectorStoreConfigValidator;
import com.agentlego.backend.vectorstore.domain.VectorStoreCollectionBindingRepository;
import com.agentlego.backend.vectorstore.domain.VectorStoreProfileAggregate;
import com.agentlego.backend.vectorstore.domain.VectorStoreProfileRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 知识库集合写路径：创建与校验（与文档入库、召回等服务解耦）。
 */
@Service
public class KbCollectionCommandService {

    private final KbCollectionRepository collectionRepository;
    private final KbDtoMapper kbDtoMapper;
    private final KbVectorStoreConfigValidator vectorStoreConfigValidator;
    private final ObjectMapper objectMapper;
    private final VectorStoreProfileRepository vectorStoreProfileRepository;
    private final VectorStoreCollectionBindingRepository collectionBindingRepository;

    public KbCollectionCommandService(
            KbCollectionRepository collectionRepository,
            KbDtoMapper kbDtoMapper,
            KbVectorStoreConfigValidator vectorStoreConfigValidator,
            ObjectMapper objectMapper,
            VectorStoreProfileRepository vectorStoreProfileRepository,
            VectorStoreCollectionBindingRepository collectionBindingRepository
    ) {
        this.collectionRepository = collectionRepository;
        this.kbDtoMapper = kbDtoMapper;
        this.vectorStoreConfigValidator = vectorStoreConfigValidator;
        this.objectMapper = objectMapper;
        this.vectorStoreProfileRepository = vectorStoreProfileRepository;
        this.collectionBindingRepository = collectionBindingRepository;
    }

    @Transactional
    public KbCollectionDto createCollection(CreateKbCollectionRequest req) {
        String name = req.getName() == null ? "" : req.getName().trim();
        if (name.isEmpty()) {
            throw new ApiException("VALIDATION_ERROR", "name 为必填", HttpStatus.BAD_REQUEST);
        }
        if (name.length() > KbLimits.MAX_COLLECTION_NAME_CHARS) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "name 过长（最多 " + KbLimits.MAX_COLLECTION_NAME_CHARS + " 字符）",
                    HttpStatus.BAD_REQUEST
            );
        }

        ApiRequires.nonBlank(req.getVectorStoreProfileId(), "vectorStoreProfileId");
        String profileId = req.getVectorStoreProfileId().trim();

        VectorStoreProfileAggregate prof = vectorStoreProfileRepository.findById(profileId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "vectorStoreProfile 未找到", HttpStatus.NOT_FOUND));
        if (req.getEmbeddingModelId() != null && !req.getEmbeddingModelId().isBlank()
                && !prof.getEmbeddingModelId().equals(req.getEmbeddingModelId().trim())) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "embeddingModelId 与 vectorStoreProfile 不一致",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (req.getVectorStoreConfig() != null) {
            for (String k : req.getVectorStoreConfig().keySet()) {
                if (!"collectionName".equals(k)) {
                    throw new ApiException(
                            "VALIDATION_ERROR",
                            "知识库须引用公共向量库：vectorStoreConfig 仅允许覆盖 collectionName（物理集合名），禁止填写 host/port 等连接信息",
                            HttpStatus.BAD_REQUEST
                    );
                }
            }
        }
        Map<String, Object> base = JsonMaps.parseObject(prof.getVectorStoreConfigJson());
        Map<String, Object> override = new HashMap<>();
        if (req.getVectorStoreConfig() != null && req.getVectorStoreConfig().get("collectionName") != null) {
            override.put("collectionName", req.getVectorStoreConfig().get("collectionName"));
        }
        Map<String, Object> merged = JsonMaps.shallowMerge(base, override);
        String collectionName = JsonMaps.getString(merged, "collectionName", "");
        if (collectionName.isBlank()) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "请在公共向量库配置中填写 collectionName，或在创建集合时于 vectorStoreConfig 中提供 collectionName",
                    HttpStatus.BAD_REQUEST
            );
        }
        KbVectorStoreKind storeKind = KbVectorStoreKind.fromApi(prof.getVectorStoreKind());
        vectorStoreConfigValidator.validate(storeKind, merged);
        String vectorStoreConfigJson;
        try {
            vectorStoreConfigJson = objectMapper.writeValueAsString(merged);
        } catch (JsonProcessingException e) {
            throw new ApiException("VALIDATION_ERROR", "vectorStoreConfig 无法序列化", HttpStatus.BAD_REQUEST);
        }
        String embeddingModelId = prof.getEmbeddingModelId();
        int embeddingDims = prof.getEmbeddingDims();
        String vectorStoreKindName = prof.getVectorStoreKind();

        KbCollectionAggregate agg = new KbCollectionAggregate();
        agg.setId(SnowflakeIdGenerator.nextId());
        agg.setName(name);
        agg.setDescription(req.getDescription() == null ? "" : req.getDescription().trim());
        agg.setEmbeddingModelId(embeddingModelId);
        agg.setEmbeddingDims(embeddingDims);
        agg.setVectorStoreKind(vectorStoreKindName);
        agg.setVectorStoreConfigJson(vectorStoreConfigJson);
        agg.setVectorStoreProfileId(profileId);
        KbChunkStrategyKind st;
        try {
            st = KbChunkStrategyKind.fromApi(req.getChunkStrategy());
        } catch (IllegalArgumentException e) {
            throw new ApiException("VALIDATION_ERROR", e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        String paramsJson;
        try {
            paramsJson = KbChunkExecutor.normalizeParamsJson(st, req.getChunkParams());
        } catch (IllegalArgumentException e) {
            throw new ApiException("VALIDATION_ERROR", e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        agg.setChunkStrategy(st.name());
        agg.setChunkParamsJson(paramsJson);
        Instant now = Instant.now();
        agg.setCreatedAt(now);
        agg.setUpdatedAt(now);
        try {
            collectionRepository.save(agg);
            collectionBindingRepository.insertKb(profileId, collectionName, agg.getId());
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(
                    "VECTOR_COLLECTION_CONFLICT",
                    "该物理 collection 已被占用或重复绑定",
                    HttpStatus.CONFLICT
            );
        }
        return kbDtoMapper.toCollectionDto(agg);
    }
}
