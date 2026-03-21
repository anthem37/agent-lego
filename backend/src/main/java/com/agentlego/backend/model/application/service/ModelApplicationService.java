package com.agentlego.backend.model.application.service;

import cn.hutool.core.util.StrUtil;
import com.agentlego.backend.agent.domain.AgentRepository;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.api.ApiRequires;
import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.model.application.dto.*;
import com.agentlego.backend.model.application.mapper.ModelDtoMapper;
import com.agentlego.backend.model.domain.ModelAggregate;
import com.agentlego.backend.model.domain.ModelProvider;
import com.agentlego.backend.model.domain.ModelRepository;
import com.agentlego.backend.model.support.ChatModelFactory;
import com.agentlego.backend.model.support.ModelConnectivityTester;
import com.agentlego.backend.model.support.ModelEmbeddingClient;
import com.agentlego.backend.runtime.definition.ModelDefinition;
import io.agentscope.core.model.Model;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * 模型应用服务（Application Service）。
 * <p>
 * 职责：
 * - 创建/查询模型配置；
 * - 连通性测试：聊天模型走流式采集；Embedding 模型走单次 embed 探测。
 * <p>
 * 注意：
 * - apiKeyCipher 字段名表示“应保存密文/引用”，当前版本仍可能为明文（MVP）。
 */
@Service
public class ModelApplicationService {

    private static final Set<String> EXECUTION_CONFIG_KEYS = Set.of(
            "timeoutSeconds",
            "maxAttempts",
            "initialBackoffSeconds",
            "maxBackoffSeconds",
            "backoffMultiplier"
    );

    private final ModelRepository modelRepository;
    private final AgentRepository agentRepository;
    private final ModelConnectivityTester connectivityTester;
    private final ChatModelFactory chatModelFactory;
    private final ModelEmbeddingClient modelEmbeddingClient;
    private final ModelDtoMapper modelDtoMapper;

    public ModelApplicationService(
            ModelRepository modelRepository,
            AgentRepository agentRepository,
            ModelConnectivityTester connectivityTester,
            ChatModelFactory chatModelFactory,
            ModelEmbeddingClient modelEmbeddingClient,
            ModelDtoMapper modelDtoMapper
    ) {
        this.modelRepository = modelRepository;
        this.agentRepository = agentRepository;
        this.connectivityTester = connectivityTester;
        this.chatModelFactory = chatModelFactory;
        this.modelEmbeddingClient = modelEmbeddingClient;
        this.modelDtoMapper = modelDtoMapper;
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

    private static String truncateForDisplay(String s, int max) {
        if (s == null) {
            return null;
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }

    private static void validateExecutionConfigValue(Object raw) {
        if (raw == null) {
            return;
        }
        Map<String, Object> ec = JsonMaps.asMap(raw);
        if (ec.isEmpty()) {
            return;
        }
        for (String k : ec.keySet()) {
            if (!EXECUTION_CONFIG_KEYS.contains(k)) {
                throw new ApiException(
                        "INVALID_MODEL_CONFIG",
                        "executionConfig 不支持键：" + k
                                + "（允许：timeoutSeconds、maxAttempts、initialBackoffSeconds、maxBackoffSeconds、backoffMultiplier）",
                        HttpStatus.BAD_REQUEST
                );
            }
        }
    }

    private static void validateToolChoiceValue(Object raw) {
        if (raw == null) {
            return;
        }
        if (raw instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                return;
            }
            if (Set.of("auto", "none", "required").contains(t.toLowerCase(Locale.ROOT))) {
                return;
            }
            throw new ApiException(
                    "INVALID_MODEL_CONFIG",
                    "toolChoice 字符串仅可为 auto、none、required",
                    HttpStatus.BAD_REQUEST
            );
        }
        Map<String, Object> m = JsonMaps.asMap(raw);
        if (m.isEmpty()) {
            return;
        }
        String toolName = JsonMaps.getString(m, "toolName", "").trim();
        if (StrUtil.isNotBlank(toolName)) {
            return;
        }
        String mode = JsonMaps.getString(m, "mode", "").trim().toLowerCase(Locale.ROOT);
        if (Set.of("auto", "none", "required").contains(mode)) {
            return;
        }
        if ("specific".equals(mode)) {
            throw new ApiException(
                    "INVALID_MODEL_CONFIG",
                    "toolChoice.mode=specific 时必须提供 toolName",
                    HttpStatus.BAD_REQUEST
            );
        }
        throw new ApiException(
                "INVALID_MODEL_CONFIG",
                "toolChoice 需提供 toolName，或 mode 为 auto/none/required",
                HttpStatus.BAD_REQUEST
        );
    }

    public String createModel(CreateModelRequest req) {
        String displayName = ApiRequires.nonBlank(req.getName(), "name").trim();
        String provider = ApiRequires.nonBlank(req.getProvider(), "provider").trim().toUpperCase();
        validateModelConfig(provider, req.getConfig());
        String modelKey = ApiRequires.nonBlank(req.getModelKey(), "modelKey").trim();

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
                .map(modelDtoMapper::toSummaryDto)
                .toList();
    }

    /**
     * 更新模型配置（provider 不可变；部分字段可按 {@link UpdateModelRequest} 语义增量更新）。
     */
    public void updateModel(String id, UpdateModelRequest req) {
        ModelAggregate agg = modelRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "模型未找到", HttpStatus.NOT_FOUND));

        String modelKey = ApiRequires.nonBlank(req.getModelKey(), "modelKey").trim();
        agg.setModelKey(modelKey);

        if (req.getName() != null) {
            agg.setName(ApiRequires.nonBlank(req.getName(), "name").trim());
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
                .orElseThrow(() -> new ApiException("NOT_FOUND", "模型未找到", HttpStatus.NOT_FOUND));
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
            throw new ApiException("NOT_FOUND", "模型未找到", HttpStatus.NOT_FOUND);
        }
    }

    public ModelDto getModel(String id) {
        ModelAggregate agg = modelRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "模型未找到", HttpStatus.NOT_FOUND));
        return modelDtoMapper.toDto(agg);
    }

    public TestModelResponse testModel(String id) {
        return testModel(id, new TestModelRequest());
    }

    /**
     * 模型测试：聊天模型采集多条流式 chunk；Embedding 模型调用一次 embed。
     *
     * @param req 可为 {@code null}，等价于空请求体
     */
    public TestModelResponse testModel(String id, TestModelRequest req) {
        ModelAggregate agg = modelRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "模型未找到", HttpStatus.NOT_FOUND));

        TestModelRequest safeReq = req == null ? new TestModelRequest() : req;

        ModelProvider provider;
        try {
            provider = ModelProvider.from(agg.getProvider());
        } catch (IllegalArgumentException e) {
            throw new ApiException(
                    "UNSUPPORTED_MODEL_PROVIDER",
                    "不支持的模型提供方：" + agg.getProvider(),
                    HttpStatus.BAD_REQUEST
            );
        }

        if (!provider.isChatProvider()) {
            return runEmbeddingProbe(agg, safeReq);
        }

        ModelDefinition def = toModelDefinition(agg);
        Model model = chatModelFactory.from(def);
        ModelConnectivityTester.ChatConnectivityResult r = connectivityTester.testChat(
                model,
                safeReq.getPrompt(),
                safeReq.getMaxTokens(),
                safeReq.getMaxStreamChunks()
        );

        TestModelResponse out = new TestModelResponse();
        out.setTestType("CHAT");
        out.setLatencyMs(r.latencyMillis());
        out.setStreamChunks(r.streamChunks());
        out.setPromptUsed(truncateForDisplay(r.promptUsed(), 600));
        out.setMaxTokensUsed(r.maxTokensUsed());

        if (r.isError()) {
            out.setStatus("ERROR");
            out.setMessage("调用失败：" + r.errorMessage());
            out.setRaw(r.errorMessage());
            return out;
        }
        if (r.isEffectivelyEmpty()) {
            out.setStatus("EMPTY");
            out.setMessage(ModelConnectivityTester.EMPTY_RESPONSE);
            out.setRaw(ModelConnectivityTester.EMPTY_RESPONSE);
            return out;
        }
        String text = r.aggregatedText().trim();
        out.setStatus("OK");
        out.setMessage(truncateForDisplay(text, 4000));
        out.setRaw(text);
        return out;
    }

    private TestModelResponse runEmbeddingProbe(ModelAggregate agg, TestModelRequest req) {
        String probe = StrUtil.isNotBlank(req.getPrompt())
                ? req.getPrompt().trim()
                : "AgentLego embedding connectivity test";
        if (probe.length() > 8000) {
            probe = probe.substring(0, 8000);
        }
        long t0 = System.nanoTime();
        try {
            List<float[]> vecs = modelEmbeddingClient.embed(agg.getId(), List.of(probe));
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            float[] v = vecs.get(0);
            int dim = v.length;
            int previewLen = Math.min(8, dim);
            List<String> parts = new ArrayList<>(previewLen);
            for (int i = 0; i < previewLen; i++) {
                parts.add(String.format("%.4f", v[i]));
            }
            String preview = "[" + String.join(", ", parts) + (dim > previewLen ? ", …]" : "]");

            TestModelResponse out = new TestModelResponse();
            out.setTestType("EMBEDDING");
            out.setStatus("OK");
            out.setLatencyMs(ms);
            out.setPromptUsed(truncateForDisplay(probe, 600));
            out.setMessage("嵌入成功，维度 " + dim + "，耗时 " + ms + " ms");
            out.setRaw(preview);
            out.setEmbeddingDimension(dim);
            out.setEmbeddingPreview(preview);
            return out;
        } catch (ApiException ex) {
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            TestModelResponse out = new TestModelResponse();
            out.setTestType("EMBEDDING");
            out.setStatus("ERROR");
            out.setLatencyMs(ms);
            out.setPromptUsed(truncateForDisplay(probe, 600));
            out.setMessage(ex.getMessage());
            out.setRaw(ex.getCode());
            return out;
        }
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
            throw new ApiException(
                    "UNSUPPORTED_MODEL_PROVIDER",
                    "不支持的模型提供方：" + provider,
                    HttpStatus.BAD_REQUEST
            );
        }
        if (config == null || config.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> e : config.entrySet()) {
            String key = e.getKey();
            if (!p.supportedConfigKeys().contains(key)) {
                throw new ApiException(
                        "INVALID_MODEL_CONFIG",
                        "不支持的配置项：" + key + "（provider=" + p.code() + "）",
                        HttpStatus.BAD_REQUEST
                );
            }
            if ("executionConfig".equals(key)) {
                validateExecutionConfigValue(e.getValue());
            } else if ("toolChoice".equals(key)) {
                validateToolChoiceValue(e.getValue());
            }
        }
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

