package com.agentlego.backend.model.support;

import cn.hutool.core.util.StrUtil;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.model.domain.ModelAggregate;
import com.agentlego.backend.model.domain.ModelProvider;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.Map;

/**
 * 与 {@link ChatModelFactory#createEmbeddingModel} 维度约定一致，供知识库入库与向量库存储校验使用。
 */
public final class ModelEmbeddingDimensions {

    /**
     * 外置向量库（如 Milvus）单字段维度上限（与常见 Milvus 部署一致，可在后续按部署调大）。
     */
    public static final int VECTOR_STORE_MAX_DIM = 8192;

    private ModelEmbeddingDimensions() {
    }

    public static int resolveOutputDimensions(ModelAggregate model) {
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
        Map<String, Object> cfg = model.getConfig() == null ? Map.of() : model.getConfig();
        if (provider == ModelProvider.OPENAI_TEXT_EMBEDDING) {
            Integer d = JsonMaps.getIntOpt(cfg, "dimensions");
            return (d != null && d > 0) ? d : 1536;
        }
        if (provider == ModelProvider.DASHSCOPE_TEXT_EMBEDDING) {
            Integer d = JsonMaps.getIntOpt(cfg, "dimensions");
            return (d != null && d > 0) ? d : 1024;
        }
        throw new ApiException(
                "UNSUPPORTED_MODEL_PROVIDER",
                "不支持的 embedding 提供方：" + providerRaw,
                HttpStatus.BAD_REQUEST
        );
    }

    /**
     * 将 embedding 输出调整为集合声明的维度：过长截断，过短则报错（避免静默错误召回）。
     */
    public static float[] fitToCollectionDim(float[] source, int expectedDim) {
        if (expectedDim <= 0 || expectedDim > VECTOR_STORE_MAX_DIM) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "embedding 维度非法或超过上限 " + VECTOR_STORE_MAX_DIM,
                    HttpStatus.BAD_REQUEST
            );
        }
        if (source == null || source.length == 0) {
            throw new ApiException("VALIDATION_ERROR", "embedding 向量为空", HttpStatus.BAD_REQUEST);
        }
        if (source.length < expectedDim) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "embedding 输出维度 " + source.length + " 小于集合配置维度 " + expectedDim,
                    HttpStatus.BAD_REQUEST
            );
        }
        if (source.length == expectedDim) {
            return source;
        }
        float[] t = new float[expectedDim];
        System.arraycopy(source, 0, t, 0, expectedDim);
        return t;
    }
}
