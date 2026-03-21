package com.agentlego.backend.model.support;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.model.domain.ModelAggregate;
import com.agentlego.backend.model.domain.ModelProvider;
import com.agentlego.backend.runtime.definition.ModelDefinition;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.embedding.dashscope.DashScopeTextEmbedding;
import io.agentscope.core.embedding.openai.OpenAITextEmbedding;
import io.agentscope.core.model.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 模型构建工厂：将平台侧定义转为推理运行时模型。
 *
 * <p>符合 DDD：Chat / Embedding 的构建逻辑统一收敛于此，避免分散在 embedding 包。
 * <ul>
 *   <li>{@link #from(ModelDefinition)}：Chat 模型，用于 Agent 推理</li>
 *   <li>{@link #createEmbeddingModel(ModelAggregate)}：Embedding 模型，用于 KB 向量化</li>
 * </ul>
 */
@Component
public class ChatModelFactory {

    private static final String DASHSCOPE_API_KEY_ENV = "DASHSCOPE_API_KEY";
    private static final String OPENAI_API_KEY_ENV = "OPENAI_API_KEY";
    private static final String ANTHROPIC_API_KEY_ENV = "ANTHROPIC_API_KEY";

    private final EnvVariables envVariables;

    public ChatModelFactory(EnvVariables envVariables) {
        this.envVariables = envVariables;
    }

    private static Optional<ExecutionConfig> buildExecutionConfigFromRoot(Map<String, Object> rootConfig) {
        Map<String, Object> ec = JsonMaps.getMap(rootConfig, "executionConfig");
        if (MapUtil.isEmpty(ec)) {
            return Optional.empty();
        }
        ExecutionConfig.Builder eb = ExecutionConfig.builder();
        boolean any = false;
        Double timeoutSec = JsonMaps.getDoubleOpt(ec, "timeoutSeconds");
        if (timeoutSec != null && timeoutSec > 0) {
            eb.timeout(Duration.ofMillis(Math.round(timeoutSec * 1000d)));
            any = true;
        }
        Integer maxAttempts = JsonMaps.getIntOpt(ec, "maxAttempts");
        if (maxAttempts != null && maxAttempts > 0) {
            eb.maxAttempts(maxAttempts);
            any = true;
        }
        Double initialBackoffSec = JsonMaps.getDoubleOpt(ec, "initialBackoffSeconds");
        if (initialBackoffSec != null && initialBackoffSec >= 0) {
            eb.initialBackoff(Duration.ofMillis(Math.round(initialBackoffSec * 1000d)));
            any = true;
        }
        Double maxBackoffSec = JsonMaps.getDoubleOpt(ec, "maxBackoffSeconds");
        if (maxBackoffSec != null && maxBackoffSec >= 0) {
            eb.maxBackoff(Duration.ofMillis(Math.round(maxBackoffSec * 1000d)));
            any = true;
        }
        Double backoffMultiplier = JsonMaps.getDoubleOpt(ec, "backoffMultiplier");
        if (backoffMultiplier != null && backoffMultiplier > 0) {
            eb.backoffMultiplier(backoffMultiplier);
            any = true;
        }
        return any ? Optional.of(eb.build()) : Optional.empty();
    }

    /**
     * 将平台 config 中的 {@code toolChoice} 转为运行时 {@link ToolChoice}。
     * <ul>
     *     <li>字符串：{@code auto} / {@code none} / {@code required}</li>
     *     <li>对象：{@code { "toolName": "x" }} 或 {@code { "mode": "specific", "toolName": "x" }}</li>
     *     <li>对象：{@code { "mode": "auto" | "none" | "required" }}</li>
     * </ul>
     */
    private static ToolChoice parseToolChoice(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String s) {
            String t = s.trim().toLowerCase(Locale.ROOT);
            return switch (t) {
                case "auto" -> new ToolChoice.Auto();
                case "none" -> new ToolChoice.None();
                case "required" -> new ToolChoice.Required();
                default -> null;
            };
        }
        Map<String, Object> m = JsonMaps.asMap(raw);
        if (m.isEmpty()) {
            return null;
        }
        String toolNameDirect = JsonMaps.getString(m, "toolName", "").trim();
        if (StrUtil.isNotBlank(toolNameDirect)) {
            return new ToolChoice.Specific(toolNameDirect);
        }
        String mode = JsonMaps.getString(m, "mode", "").trim().toLowerCase(Locale.ROOT);
        if ("specific".equals(mode)) {
            String tn = JsonMaps.getString(m, "toolName", "").trim();
            if (StrUtil.isNotBlank(tn)) {
                return new ToolChoice.Specific(tn);
            }
            return null;
        }
        return switch (mode) {
            case "auto" -> new ToolChoice.Auto();
            case "none" -> new ToolChoice.None();
            case "required" -> new ToolChoice.Required();
            default -> null;
        };
    }

    /**
     * Chat 模型：将 {@link ModelDefinition} 转为运行时 {@link Model}。
     */
    public Model from(ModelDefinition modelDef) {
        if (modelDef == null) {
            throw new IllegalArgumentException("modelDef is required");
        }
        String providerRaw = StrUtil.blankToDefault(StrUtil.trim(modelDef.provider()), "")
                .toUpperCase(Locale.ROOT);
        ModelProvider provider = ModelProvider.from(providerRaw);
        if (!provider.isChatProvider()) {
            throw new IllegalArgumentException("provider is not a chat model: " + providerRaw);
        }

        Map<String, Object> config = modelDef.config() == null ? Map.of() : modelDef.config();
        String apiKey = resolveApiKey(provider, modelDef.apiKey(), config);
        String baseUrl = StrUtil.trimToEmpty(modelDef.baseUrl());
        GenerateOptions defaultOptions = buildGenerateOptions(config);

        if (provider == ModelProvider.DASHSCOPE) {
            return buildDashScope(modelDef, apiKey, baseUrl, defaultOptions);
        }
        if (provider == ModelProvider.OPENAI) {
            return buildOpenAI(modelDef, apiKey, baseUrl, defaultOptions, config);
        }
        if (provider == ModelProvider.ANTHROPIC) {
            return buildAnthropic(modelDef, apiKey, baseUrl, defaultOptions);
        }
        throw new IllegalArgumentException("Unsupported model provider: " + providerRaw);
    }

    private Model buildDashScope(ModelDefinition def, String apiKey, String baseUrl, GenerateOptions opts) {
        if (StrUtil.isBlank(apiKey)) {
            throw new IllegalArgumentException("DASHSCOPE apiKey is required");
        }
        DashScopeChatModel.Builder b = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(def.modelName())
                .defaultOptions(opts);
        if (!baseUrl.isBlank()) {
            b.baseUrl(baseUrl);
        }
        return b.build();
    }

    private Model buildOpenAI(ModelDefinition def,
                              String apiKey,
                              String baseUrl,
                              GenerateOptions opts,
                              Map<String, Object> config) {
        if (StrUtil.isBlank(apiKey)) {
            throw new IllegalArgumentException("OPENAI apiKey is required");
        }
        OpenAIChatModel.Builder b = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(def.modelName())
                .generateOptions(opts);
        if (!baseUrl.isBlank()) {
            b.baseUrl(baseUrl);
        }
        String endpointPath = JsonMaps.getString(config, "endpointPath", "");
        if (StrUtil.isNotBlank(endpointPath)) {
            b.endpointPath(endpointPath);
        }
        return b.build();
    }

    private Model buildAnthropic(ModelDefinition def, String apiKey, String baseUrl, GenerateOptions opts) {
        if (StrUtil.isBlank(apiKey)) {
            throw new IllegalArgumentException("ANTHROPIC apiKey is required");
        }
        AnthropicChatModel.Builder b = AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(def.modelName())
                .defaultOptions(opts);
        if (StrUtil.isNotBlank(baseUrl)) {
            b.baseUrl(baseUrl);
        }
        return b.build();
    }

    private String resolveApiKey(ModelProvider provider, String explicitApiKey, Map<String, Object> config) {
        String key = StrUtil.trimToEmpty(explicitApiKey);
        if (StrUtil.isNotBlank(key)) {
            return key;
        }
        String configApiKey = JsonMaps.getString(config, "apiKey", "");
        if (StrUtil.isNotBlank(configApiKey)) {
            return configApiKey;
        }
        if (provider == ModelProvider.DASHSCOPE) {
            return StrUtil.trimToEmpty(envVariables.get(DASHSCOPE_API_KEY_ENV));
        }
        if (provider == ModelProvider.OPENAI) {
            return StrUtil.trimToEmpty(envVariables.get(OPENAI_API_KEY_ENV));
        }
        if (provider == ModelProvider.ANTHROPIC) {
            return StrUtil.trimToEmpty(envVariables.get(ANTHROPIC_API_KEY_ENV));
        }
        return "";
    }

    private GenerateOptions buildGenerateOptions(Map<String, Object> config) {
        Map<String, Object> safe = config == null ? Map.of() : config;
        GenerateOptions.Builder b = GenerateOptions.builder();

        Double temperature = JsonMaps.getDoubleOpt(safe, "temperature");
        if (temperature != null) {
            b.temperature(temperature);
        }
        Double topP = JsonMaps.getDoubleOpt(safe, "topP");
        if (topP != null) {
            b.topP(topP);
        }
        Integer topK = JsonMaps.getIntOpt(safe, "topK");
        if (topK != null) {
            b.topK(topK);
        }
        Integer maxTokens = JsonMaps.getIntOpt(safe, "maxTokens");
        if (maxTokens != null) {
            b.maxTokens(maxTokens);
        }
        Integer maxCompletionTokens = JsonMaps.getIntOpt(safe, "maxCompletionTokens");
        if (maxCompletionTokens != null) {
            b.maxCompletionTokens(maxCompletionTokens);
        }
        Long seed = JsonMaps.getLongOpt(safe, "seed");
        if (seed != null) {
            b.seed(seed);
        }
        Map<String, String> additionalHeaders = JsonMaps.getStringMap(safe, "additionalHeaders");
        if (!additionalHeaders.isEmpty()) {
            b.additionalHeaders(additionalHeaders);
        }
        Map<String, Object> additionalBodyParams = JsonMaps.getMap(safe, "additionalBodyParams");
        if (!additionalBodyParams.isEmpty()) {
            b.additionalBodyParams(additionalBodyParams);
        }
        Map<String, String> additionalQueryParams = JsonMaps.getStringMap(safe, "additionalQueryParams");
        if (!additionalQueryParams.isEmpty()) {
            b.additionalQueryParams(additionalQueryParams);
        }

        Boolean stream = JsonMaps.getBooleanOpt(safe, "stream");
        if (stream != null) {
            b.stream(stream);
        }
        Double frequencyPenalty = JsonMaps.getDoubleOpt(safe, "frequencyPenalty");
        if (frequencyPenalty != null) {
            b.frequencyPenalty(frequencyPenalty);
        }
        Double presencePenalty = JsonMaps.getDoubleOpt(safe, "presencePenalty");
        if (presencePenalty != null) {
            b.presencePenalty(presencePenalty);
        }
        Integer thinkingBudget = JsonMaps.getIntOpt(safe, "thinkingBudget");
        if (thinkingBudget != null) {
            b.thinkingBudget(thinkingBudget);
        }
        String reasoningEffort = JsonMaps.getString(safe, "reasoningEffort", "");
        if (StrUtil.isNotBlank(reasoningEffort)) {
            b.reasoningEffort(reasoningEffort.trim());
        }
        buildExecutionConfigFromRoot(safe).ifPresent(b::executionConfig);
        ToolChoice toolChoice = parseToolChoice(safe.get("toolChoice"));
        if (toolChoice != null) {
            b.toolChoice(toolChoice);
        }
        return b.build();
    }

    /**
     * Embedding 模型：将 {@link ModelAggregate} 转为运行时 {@link EmbeddingModel}。
     */
    public EmbeddingModel createEmbeddingModel(ModelAggregate model) {
        if (model == null) {
            throw new ApiException("VALIDATION_ERROR", "模型聚合不能为空", HttpStatus.BAD_REQUEST);
        }
        String providerRaw = StrUtil.blankToDefault(StrUtil.trim(model.getProvider()), "")
                .toUpperCase(Locale.ROOT);
        ModelProvider provider = ModelProvider.from(providerRaw);
        if (provider.isChatProvider()) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "embeddingModelId must point to an embedding provider model; got " + providerRaw,
                    HttpStatus.BAD_REQUEST
            );
        }

        String apiKey = StrUtil.trimToEmpty(model.getApiKeyCipher());
        String safeApiKey = apiKey.isBlank() ? null : apiKey.trim();
        Map<String, Object> cfg = model.getConfig() == null ? Map.of() : model.getConfig();
        String baseUrl = StrUtil.trimToEmpty(model.getBaseUrl());
        String baseUrlOrNull = baseUrl.isBlank() ? null : baseUrl;
        Optional<ExecutionConfig> executionConfig = buildExecutionConfigFromRoot(cfg);

        if (provider == ModelProvider.OPENAI_TEXT_EMBEDDING) {
            Integer d = JsonMaps.getIntOpt(cfg, "dimensions");
            int dimensions = (d != null && d > 0) ? d : 1536;
            ExecutionConfig ec = executionConfig.orElse(null);
            if (StrUtil.isBlank(safeApiKey)) {
                return new OpenAITextEmbedding(null, model.getModelKey(), dimensions, ec, baseUrlOrNull);
            }
            return OpenAITextEmbedding.builder()
                    .apiKey(safeApiKey)
                    .modelName(model.getModelKey())
                    .dimensions(dimensions)
                    .executionConfig(ec)
                    .baseUrl(baseUrlOrNull)
                    .build();
        }
        if (provider == ModelProvider.DASHSCOPE_TEXT_EMBEDDING) {
            Integer d = JsonMaps.getIntOpt(cfg, "dimensions");
            int dimensions = (d != null && d > 0) ? d : 1024;
            ExecutionConfig ec = executionConfig.orElse(null);
            if (StrUtil.isBlank(safeApiKey)) {
                return new DashScopeTextEmbedding(null, model.getModelKey(), dimensions, ec, baseUrlOrNull);
            }
            return DashScopeTextEmbedding.builder()
                    .apiKey(safeApiKey)
                    .modelName(model.getModelKey())
                    .dimensions(dimensions)
                    .executionConfig(ec)
                    .baseUrl(baseUrlOrNull)
                    .build();
        }
        throw new ApiException(
                "UNSUPPORTED_MODEL_PROVIDER",
                "不支持的 embedding 提供方：" + providerRaw,
                HttpStatus.BAD_REQUEST
        );
    }
}
