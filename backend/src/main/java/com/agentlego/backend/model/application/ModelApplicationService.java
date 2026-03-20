package com.agentlego.backend.model.application;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.model.domain.ModelAggregate;
import com.agentlego.backend.model.domain.ModelProvider;
import com.agentlego.backend.model.domain.ModelRepository;
import com.agentlego.backend.model.dto.CreateModelRequest;
import com.agentlego.backend.model.dto.ModelDto;
import com.agentlego.backend.model.dto.TestModelResponse;
import com.agentlego.backend.runtime.AgentRuntime;
import com.agentlego.backend.runtime.definition.AgentDefinition;
import com.agentlego.backend.runtime.definition.ModelDefinition;
import io.agentscope.core.message.Msg;
import io.agentscope.core.tool.Toolkit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
/**
 * 模型应用服务（Application Service）。
 *
 * 职责：
 * - 创建/查询模型配置；
 * - 提供最小“连通性测试”（当前仅用于验证调用链是否可跑通）。
 *
 * 注意：
 * - apiKeyCipher 字段名表示“应保存密文/引用”，当前版本仍可能为明文（MVP）。
 */
public class ModelApplicationService {
    private static final String PROVIDER_DASHSCOPE = "DASHSCOPE";
    private static final Duration MODEL_TEST_TIMEOUT = Duration.ofMinutes(2);
    private final ModelRepository modelRepository;
    private final AgentRuntime agentRuntime;

    public ModelApplicationService(ModelRepository modelRepository, AgentRuntime agentRuntime) {
        this.modelRepository = modelRepository;
        this.agentRuntime = agentRuntime;
    }

    public String createModel(CreateModelRequest req) {
        String provider = requireNonBlank(req.getProvider(), "provider").trim().toUpperCase();
        validateModelConfig(provider, req.getConfig());
        String modelKey = requireNonBlank(req.getModelKey(), "modelKey").trim();

        ModelAggregate aggregate = new ModelAggregate();
        aggregate.setId(com.agentlego.backend.common.SnowflakeIdGenerator.nextId());
        aggregate.setProvider(provider);
        aggregate.setModelKey(modelKey);
        aggregate.setApiKeyCipher(req.getApiKey());
        aggregate.setBaseUrl(req.getBaseUrl() == null ? null : req.getBaseUrl().trim());
        aggregate.setConfig(req.getConfig() == null ? Map.of() : req.getConfig());
        aggregate.setCreatedAt(Instant.now());
        return modelRepository.save(aggregate);
    }

    public ModelDto getModel(String id) {
        ModelAggregate agg = modelRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "model not found", HttpStatus.NOT_FOUND));

        ModelDto dto = new ModelDto();
        dto.setId(agg.getId());
        dto.setProvider(agg.getProvider());
        dto.setModelKey(agg.getModelKey());
        dto.setConfig(agg.getConfig());
        dto.setBaseUrl(agg.getBaseUrl());
        dto.setCreatedAt(agg.getCreatedAt());
        return dto;
    }

    public TestModelResponse testModel(String id) {
        ModelAggregate agg = modelRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "model not found", HttpStatus.NOT_FOUND));

        if (!PROVIDER_DASHSCOPE.equalsIgnoreCase(agg.getProvider())) {
            throw new ApiException(
                    "UNSUPPORTED_MODEL_PROVIDER",
                    "only DASHSCOPE is supported in v1 test; got " + agg.getProvider(),
                    HttpStatus.BAD_REQUEST
            );
        }

        AgentDefinition agentDef = new AgentDefinition(
                "ModelTester",
                "You are a helpful assistant. Reply with a single word: OK.",
                new ModelDefinition(
                        agg.getProvider(),
                        agg.getModelKey(),
                        agg.getApiKeyCipher(),
                        agg.getBaseUrl(),
                        agg.getConfig()
                ),
                1
        );

        Msg msg = agentRuntime.call(agentDef, "test", new Toolkit()).block(MODEL_TEST_TIMEOUT);
        String text = (msg == null || msg.getTextContent() == null || msg.getTextContent().isBlank())
                ? "EMPTY_RESPONSE"
                : msg.getTextContent().trim();
        return new TestModelResponse(text, text);
    }

    private String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException("VALIDATION_ERROR", fieldName + " is required", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private void validateModelConfig(String provider, Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return;
        }
        try {
            ModelProvider p = ModelProvider.from(provider);
            for (String key : config.keySet()) {
                if (!p.supportedConfigKeys().contains(key)) {
                    throw new ApiException(
                            "INVALID_MODEL_CONFIG",
                            "unsupported config key for " + p.code() + ": " + key,
                            HttpStatus.BAD_REQUEST
                    );
                }
            }
        } catch (IllegalArgumentException e) {
            throw new ApiException("UNSUPPORTED_MODEL_PROVIDER", e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}

