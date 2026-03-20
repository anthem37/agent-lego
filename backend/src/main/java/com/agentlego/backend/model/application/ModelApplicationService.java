package com.agentlego.backend.model.application;

import com.agentlego.backend.agent.domain.AgentRepository;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.model.domain.ModelAggregate;
import com.agentlego.backend.model.domain.ModelProvider;
import com.agentlego.backend.model.domain.ModelRepository;
import com.agentlego.backend.model.dto.*;
import com.agentlego.backend.model.support.ChatModelFactory;
import com.agentlego.backend.model.support.ModelConfigSummaries;
import com.agentlego.backend.model.support.ModelConnectivityTester;
import com.agentlego.backend.runtime.definition.ModelDefinition;
import io.agentscope.core.model.Model;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 模型应用服务（Application Service）。
 * <p>
 * 职责：
 * - 创建/查询模型配置；
 * - 连通性测试（通过 ChatModelFactory + ModelConnectivityTester，支持全部 chat provider）。
 * <p>
 * 注意：
 * - apiKeyCipher 字段名表示“应保存密文/引用”，当前版本仍可能为明文（MVP）。
 */
@Service
public class ModelApplicationService {
    private final ModelRepository modelRepository;
    private final AgentRepository agentRepository;
    private final ModelConnectivityTester connectivityTester;
    private final ChatModelFactory chatModelFactory;

    public ModelApplicationService(
            ModelRepository modelRepository,
            AgentRepository agentRepository,
            ModelConnectivityTester connectivityTester,
            ChatModelFactory chatModelFactory
    ) {
        this.modelRepository = modelRepository;
        this.agentRepository = agentRepository;
        this.connectivityTester = connectivityTester;
        this.chatModelFactory = chatModelFactory;
    }

    private static ModelDefinition toModelDefinition(ModelAggregate agg) {
        return new ModelDefinition(
                agg.getProvider(),
                agg.getModelKey(),
                agg.getApiKeyCipher(),
                agg.getBaseUrl(),
                agg.getConfig() == null ? Map.of() : agg.getConfig()
        );
    }

    public String createModel(CreateModelRequest req) {
        String displayName = requireNonBlank(req.getName(), "name").trim();
        String provider = requireNonBlank(req.getProvider(), "provider").trim().toUpperCase();
        validateModelConfig(provider, req.getConfig());
        String modelKey = requireNonBlank(req.getModelKey(), "modelKey").trim();

        ModelAggregate aggregate = new ModelAggregate();
        aggregate.setId(com.agentlego.backend.common.SnowflakeIdGenerator.nextId());
        aggregate.setName(displayName);
        aggregate.setDescription(normalizeDescription(req.getDescription()));
        aggregate.setProvider(provider);
        aggregate.setModelKey(modelKey);
        aggregate.setApiKeyCipher(req.getApiKey());
        aggregate.setBaseUrl(req.getBaseUrl() == null ? null : req.getBaseUrl().trim());
        aggregate.setConfig(req.getConfig() == null ? Map.of() : req.getConfig());
        aggregate.setCreatedAt(Instant.now());
        return modelRepository.save(aggregate);
    }

    /**
     * 模型列表（按创建时间倒序）。
     */
    public List<ModelSummaryDto> listModels() {
        return modelRepository.findAllOrderByCreatedAtDesc().stream()
                .map(this::toSummaryDto)
                .toList();
    }

    /**
     * 更新模型配置（provider 不可变；部分字段可按 {@link UpdateModelRequest} 语义增量更新）。
     */
    public void updateModel(String id, UpdateModelRequest req) {
        ModelAggregate agg = modelRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "model not found", HttpStatus.NOT_FOUND));

        String modelKey = requireNonBlank(req.getModelKey(), "modelKey").trim();
        agg.setModelKey(modelKey);

        if (req.getName() != null) {
            agg.setName(requireNonBlank(req.getName(), "name").trim());
        }

        if (req.getDescription() != null) {
            agg.setDescription(normalizeDescription(req.getDescription()));
        }

        if (req.getBaseUrl() != null) {
            String bu = req.getBaseUrl().trim();
            agg.setBaseUrl(bu.isEmpty() ? null : bu);
        }

        if (req.getConfig() != null) {
            validateModelConfig(agg.getProvider(), req.getConfig());
            agg.setConfig(req.getConfig());
        }

        if (req.getApiKey() != null) {
            String key = req.getApiKey().trim();
            agg.setApiKeyCipher(key.isEmpty() ? null : key);
        }

        modelRepository.update(agg);
    }

    /**
     * 删除模型；若仍被智能体引用则拒绝删除。
     */
    public void deleteModel(String id) {
        modelRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "model not found", HttpStatus.NOT_FOUND));
        int refs = agentRepository.countByModelId(id);
        if (refs > 0) {
            throw new ApiException(
                    "CONFLICT",
                    "模型仍被智能体引用，请先解绑或调整智能体后再删除",
                    HttpStatus.CONFLICT
            );
        }
        int deleted = modelRepository.deleteById(id);
        if (deleted == 0) {
            throw new ApiException("NOT_FOUND", "model not found", HttpStatus.NOT_FOUND);
        }
    }

    public ModelDto getModel(String id) {
        ModelAggregate agg = modelRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "model not found", HttpStatus.NOT_FOUND));

        ModelDto dto = new ModelDto();
        dto.setId(agg.getId());
        dto.setName(agg.getName());
        dto.setDescription(agg.getDescription());
        dto.setProvider(agg.getProvider());
        dto.setModelKey(agg.getModelKey());
        dto.setConfig(agg.getConfig());
        dto.setBaseUrl(agg.getBaseUrl());
        dto.setCreatedAt(agg.getCreatedAt());
        String cipher = agg.getApiKeyCipher();
        dto.setApiKeyConfigured(cipher != null && !cipher.isBlank());
        return dto;
    }

    public TestModelResponse testModel(String id) {
        ModelAggregate agg = modelRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "model not found", HttpStatus.NOT_FOUND));

        ModelProvider provider;
        try {
            provider = ModelProvider.from(agg.getProvider());
        } catch (IllegalArgumentException e) {
            throw new ApiException("UNSUPPORTED_MODEL_PROVIDER", e.getMessage(), HttpStatus.BAD_REQUEST);
        }

        if (!provider.isChatProvider()) {
            return new TestModelResponse("EMBEDDING_TEST_SKIPPED", "EMBEDDING_TEST_SKIPPED");
        }

        ModelDefinition def = toModelDefinition(agg);
        Model model = chatModelFactory.from(def);
        String text = connectivityTester.test(model);
        return new TestModelResponse(text, text);
    }

    private String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException("VALIDATION_ERROR", fieldName + " is required", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    /**
     * 校验 provider 必须在 {@link ModelProvider} 内；若带 config 则键名须在提供方白名单内。
     * <p>
     * 注意：原先 config 为空时直接 return，会导致任意字符串 provider 被写入库（仅创建场景），运行时/测试才暴露问题。
     */
    private void validateModelConfig(String provider, Map<String, Object> config) {
        final ModelProvider p;
        try {
            p = ModelProvider.from(provider);
        } catch (IllegalArgumentException e) {
            throw new ApiException("UNSUPPORTED_MODEL_PROVIDER", e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        if (config == null || config.isEmpty()) {
            return;
        }
        for (String key : config.keySet()) {
            if (!p.supportedConfigKeys().contains(key)) {
                throw new ApiException(
                        "INVALID_MODEL_CONFIG",
                        "unsupported config key for " + p.code() + ": " + key,
                        HttpStatus.BAD_REQUEST
                );
            }
        }
    }

    private ModelSummaryDto toSummaryDto(ModelAggregate agg) {
        ModelSummaryDto dto = new ModelSummaryDto();
        dto.setId(agg.getId());
        dto.setName(agg.getName());
        dto.setDescription(agg.getDescription());
        dto.setProvider(agg.getProvider());
        dto.setModelKey(agg.getModelKey());
        dto.setBaseUrl(agg.getBaseUrl());
        dto.setConfigSummary(ModelConfigSummaries.summarize(agg.getConfig()));
        dto.setCreatedAt(agg.getCreatedAt());
        return dto;
    }

    /**
     * 空串视为「无备注」；{@code null} 在更新语义中由调用方区分是否修改。
     */
    private String normalizeDescription(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }
}

