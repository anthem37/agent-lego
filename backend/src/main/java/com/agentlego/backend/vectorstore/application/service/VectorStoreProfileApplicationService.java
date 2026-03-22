package com.agentlego.backend.vectorstore.application.service;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.api.ApiRequires;
import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.kb.domain.KbVectorStoreKind;
import com.agentlego.backend.kb.infrastructure.persistence.KbCollectionMapper;
import com.agentlego.backend.kb.vector.KbVectorStoreConfigValidator;
import com.agentlego.backend.model.domain.ModelAggregate;
import com.agentlego.backend.model.domain.ModelRepository;
import com.agentlego.backend.model.support.ModelEmbeddingDimensions;
import com.agentlego.backend.vectorstore.application.dto.CreateVectorStoreProfileRequest;
import com.agentlego.backend.vectorstore.application.dto.UpdateVectorStoreProfileRequest;
import com.agentlego.backend.vectorstore.application.dto.VectorStoreProfileDto;
import com.agentlego.backend.vectorstore.domain.VectorStoreProfileAggregate;
import com.agentlego.backend.vectorstore.domain.VectorStoreProfileRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 外置向量库连接配置：知识库向量写入与检索；ANN 不经过 PostgreSQL。
 */
@Service
public class VectorStoreProfileApplicationService {

    private final VectorStoreProfileRepository repository;
    private final ModelRepository modelRepository;
    private final KbCollectionMapper kbCollectionMapper;
    private final KbVectorStoreConfigValidator vectorStoreConfigValidator;
    private final ObjectMapper objectMapper;

    public VectorStoreProfileApplicationService(
            VectorStoreProfileRepository repository,
            ModelRepository modelRepository,
            KbCollectionMapper kbCollectionMapper,
            KbVectorStoreConfigValidator vectorStoreConfigValidator,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.modelRepository = modelRepository;
        this.kbCollectionMapper = kbCollectionMapper;
        this.vectorStoreConfigValidator = vectorStoreConfigValidator;
        this.objectMapper = objectMapper;
    }

    public List<VectorStoreProfileDto> listProfiles() {
        return repository.listAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public VectorStoreProfileDto getProfile(String id) {
        return repository.findById(ApiRequires.nonBlank(id, "id"))
                .map(this::toDto)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "vectorStoreProfile 未找到", HttpStatus.NOT_FOUND));
    }

    public VectorStoreProfileDto createProfile(CreateVectorStoreProfileRequest req) {
        String name = ApiRequires.nonBlank(req.getName(), "name").trim();
        KbVectorStoreKind kind = KbVectorStoreKind.fromApi(req.getVectorStoreKind());
        if (kind != KbVectorStoreKind.MILVUS && kind != KbVectorStoreKind.QDRANT) {
            throw new ApiException("VALIDATION_ERROR", "仅支持 MILVUS、QDRANT", HttpStatus.BAD_REQUEST);
        }
        if (req.getVectorStoreConfig() == null || req.getVectorStoreConfig().isEmpty()) {
            throw new ApiException("VALIDATION_ERROR", "vectorStoreConfig 为必填", HttpStatus.BAD_REQUEST);
        }
        vectorStoreConfigValidator.validate(kind, req.getVectorStoreConfig(), false);

        ModelAggregate embModel = modelRepository.findById(req.getEmbeddingModelId().trim())
                .orElseThrow(() -> new ApiException("NOT_FOUND", "embedding 模型未找到", HttpStatus.NOT_FOUND));
        int outDims = ModelEmbeddingDimensions.resolveOutputDimensions(embModel);
        if (outDims > ModelEmbeddingDimensions.VECTOR_STORE_MAX_DIM) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "embedding 维度超过向量库上限 " + ModelEmbeddingDimensions.VECTOR_STORE_MAX_DIM,
                    HttpStatus.BAD_REQUEST
            );
        }

        String configJson;
        try {
            configJson = objectMapper.writeValueAsString(req.getVectorStoreConfig());
        } catch (JsonProcessingException e) {
            throw new ApiException("VALIDATION_ERROR", "vectorStoreConfig 无法序列化", HttpStatus.BAD_REQUEST);
        }

        Instant now = Instant.now();
        VectorStoreProfileAggregate agg = new VectorStoreProfileAggregate();
        agg.setId(SnowflakeIdGenerator.nextId());
        agg.setName(name);
        agg.setVectorStoreKind(kind.name());
        agg.setVectorStoreConfigJson(configJson);
        agg.setEmbeddingModelId(req.getEmbeddingModelId().trim());
        agg.setEmbeddingDims(outDims);
        agg.setCreatedAt(now);
        agg.setUpdatedAt(now);
        repository.save(agg);
        return toDto(agg);
    }

    public VectorStoreProfileDto updateProfile(String id, UpdateVectorStoreProfileRequest req) {
        VectorStoreProfileAggregate agg = repository.findById(ApiRequires.nonBlank(id, "id"))
                .orElseThrow(() -> new ApiException("NOT_FOUND", "vectorStoreProfile 未找到", HttpStatus.NOT_FOUND));
        if (req.getName() != null && !req.getName().isBlank()) {
            agg.setName(req.getName().trim());
        }
        if (req.getVectorStoreKind() != null && !req.getVectorStoreKind().isBlank()) {
            KbVectorStoreKind kind = KbVectorStoreKind.fromApi(req.getVectorStoreKind());
            agg.setVectorStoreKind(kind.name());
        }
        if (req.getVectorStoreConfig() != null && !req.getVectorStoreConfig().isEmpty()) {
            KbVectorStoreKind kind = KbVectorStoreKind.fromApi(agg.getVectorStoreKind());
            vectorStoreConfigValidator.validate(kind, req.getVectorStoreConfig(), false);
            try {
                agg.setVectorStoreConfigJson(objectMapper.writeValueAsString(req.getVectorStoreConfig()));
            } catch (JsonProcessingException e) {
                throw new ApiException("VALIDATION_ERROR", "vectorStoreConfig 无法序列化", HttpStatus.BAD_REQUEST);
            }
        }
        if (req.getEmbeddingModelId() != null && !req.getEmbeddingModelId().isBlank()) {
            ModelAggregate embModel = modelRepository.findById(req.getEmbeddingModelId().trim())
                    .orElseThrow(() -> new ApiException("NOT_FOUND", "embedding 模型未找到", HttpStatus.NOT_FOUND));
            agg.setEmbeddingModelId(req.getEmbeddingModelId().trim());
            agg.setEmbeddingDims(ModelEmbeddingDimensions.resolveOutputDimensions(embModel));
        }
        agg.setUpdatedAt(Instant.now());
        KbVectorStoreKind kind = KbVectorStoreKind.fromApi(agg.getVectorStoreKind());
        vectorStoreConfigValidator.validate(kind, JsonMaps.parseObject(agg.getVectorStoreConfigJson()), false);
        repository.update(agg);
        return getProfile(id);
    }

    public void deleteProfile(String id) {
        String pid = ApiRequires.nonBlank(id, "id");
        repository.findById(pid)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "vectorStoreProfile 未找到", HttpStatus.NOT_FOUND));
        int kbRefs = kbCollectionMapper.countByVectorStoreProfileId(pid);
        if (kbRefs > 0) {
            throw new ApiException(
                    "CONFLICT",
                    "仍有 "
                            + kbRefs
                            + " 个知识库集合引用该向量库连接，请先在「知识库」中删除对应集合或更换其向量库配置后再删除。",
                    HttpStatus.CONFLICT
            );
        }
        int n = repository.deleteById(pid);
        if (n == 0) {
            throw new ApiException("NOT_FOUND", "vectorStoreProfile 未找到", HttpStatus.NOT_FOUND);
        }
    }

    private VectorStoreProfileDto toDto(VectorStoreProfileAggregate a) {
        VectorStoreProfileDto d = new VectorStoreProfileDto();
        d.setId(a.getId());
        d.setName(a.getName());
        d.setVectorStoreKind(a.getVectorStoreKind());
        d.setVectorStoreConfig(JsonMaps.parseObject(a.getVectorStoreConfigJson()));
        d.setEmbeddingModelId(a.getEmbeddingModelId());
        d.setEmbeddingDims(a.getEmbeddingDims());
        d.setCreatedAt(a.getCreatedAt());
        d.setUpdatedAt(a.getUpdatedAt());
        return d;
    }
}
