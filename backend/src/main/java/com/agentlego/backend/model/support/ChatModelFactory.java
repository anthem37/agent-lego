package com.agentlego.backend.model.support;

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

import java.util.Locale;
import java.util.Map;

/**
 * 模型构建工厂：将平台侧定义转为 AgentScope 运行时模型。
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

    /**
     * Chat 模型：将 {@link ModelDefinition} 转为 AgentScope {@link Model}。
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
        return b.build();
    }

    /**
     * Embedding 模型：将 {@link ModelAggregate} 转为 AgentScope {@link EmbeddingModel}。
     */
    public EmbeddingModel createEmbeddingModel(ModelAggregate model) {
        if (model == null) {
            throw new ApiException("VALIDATION_ERROR", "model aggregate is required", HttpStatus.BAD_REQUEST);
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
        String encodingFormat = JsonMaps.getString(cfg, "encodingFormat", "");
        String baseUrl = StrUtil.trimToEmpty(model.getBaseUrl());
        String baseUrlOrNull = baseUrl.isBlank() ? null : baseUrl;

        if (provider == ModelProvider.OPENAI_TEXT_EMBEDDING) {
            Integer d = JsonMaps.getIntOpt(cfg, "dimensions");
            int dimensions = (d != null && d > 0) ? d : 1536;
            if (StrUtil.isNotBlank(encodingFormat) && !"float".equalsIgnoreCase(encodingFormat)) {
                throw new ApiException(
                        "VALIDATION_ERROR",
                        "OpenAITextEmbedding only supports encodingFormat=float for KB search",
                        HttpStatus.BAD_REQUEST
                );
            }
            return new OpenAITextEmbedding(safeApiKey, model.getModelKey(), dimensions, null, baseUrlOrNull);
        }
        if (provider == ModelProvider.DASHSCOPE_TEXT_EMBEDDING) {
            Integer d = JsonMaps.getIntOpt(cfg, "dimensions");
            int dimensions = (d != null && d > 0) ? d : 1024;
            return new DashScopeTextEmbedding(safeApiKey, model.getModelKey(), dimensions, null, baseUrlOrNull);
        }
        throw new ApiException(
                "UNSUPPORTED_MODEL_PROVIDER",
                "unsupported embedding provider: " + providerRaw,
                HttpStatus.BAD_REQUEST
        );
    }
}
