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
 * 与 {@link ChatModelFactory#createEmbeddingModel} 维度约定一致，供知识库入库/pgvector padding 使用。
 */
public final class ModelEmbeddingDimensions {

    /**
     * pgvector 列固定维度：覆盖 OpenAI 3-large(3072) 及以下常见模型；更长的向量将截断。
     */
    public static final int PGVECTOR_STORED_DIM = 3072;

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
     * 将上游向量 padding / 截断到 {@link #PGVECTOR_STORED_DIM}，用于写入 {@code vector(3072)}。
     */
    public static float[] padForPgStorage(float[] source) {
        if (source == null || source.length == 0) {
            return new float[PGVECTOR_STORED_DIM];
        }
        if (source.length > PGVECTOR_STORED_DIM) {
            float[] t = new float[PGVECTOR_STORED_DIM];
            System.arraycopy(source, 0, t, 0, PGVECTOR_STORED_DIM);
            return t;
        }
        if (source.length == PGVECTOR_STORED_DIM) {
            return source;
        }
        float[] out = new float[PGVECTOR_STORED_DIM];
        System.arraycopy(source, 0, out, 0, source.length);
        return out;
    }
}
