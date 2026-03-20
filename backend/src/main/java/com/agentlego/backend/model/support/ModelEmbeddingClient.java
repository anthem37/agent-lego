package com.agentlego.backend.model.support;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.model.domain.ModelAggregate;
import com.agentlego.backend.model.domain.ModelRepository;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.message.TextBlock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Embedding 能力封装（面向向量化检索）。
 *
 * <p>领域边界：embedding 模型的调用属于「模型」能力，KB 只负责分片与组装检索结果。
 * <p>DDD：构建逻辑委托给 {@link ChatModelFactory#createEmbeddingModel}，本类仅负责编排与调用。
 */
@Service
public class ModelEmbeddingClient {

    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(120);

    private final ModelRepository modelRepository;
    private final ChatModelFactory chatModelFactory;

    public ModelEmbeddingClient(ModelRepository modelRepository, ChatModelFactory chatModelFactory) {
        this.modelRepository = modelRepository;
        this.chatModelFactory = chatModelFactory;
    }

    public List<float[]> embed(String embeddingModelId, List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        Objects.requireNonNull(embeddingModelId, "embeddingModelId");

        ModelAggregate model = modelRepository.findById(embeddingModelId)
                .orElseThrow(() -> new ApiException(
                        "NOT_FOUND",
                        "embedding 模型未找到：" + embeddingModelId,
                        HttpStatus.NOT_FOUND
                ));

        EmbeddingModel embedder = chatModelFactory.createEmbeddingModel(model);

        List<float[]> out = new ArrayList<>(texts.size());
        for (String text : texts) {
            String safe = text == null ? "" : text;
            try {
                double[] vec = embedder.embed(TextBlock.builder().text(safe).build()).block(BLOCK_TIMEOUT);
                if (vec == null || vec.length == 0) {
                    throw new ApiException(
                            "UPSTREAM_ERROR",
                            "embedding 返回空结果，modelId=" + embeddingModelId,
                            HttpStatus.BAD_GATEWAY
                    );
                }
                float[] f = new float[vec.length];
                for (int i = 0; i < vec.length; i++) {
                    f[i] = (float) vec[i];
                }
                out.add(f);
            } catch (Exception e) {
                throw new ApiException(
                        "UPSTREAM_ERROR",
                        "embedding 失败：" + (e.getMessage() == null || e.getMessage().isBlank()
                                ? e.getClass().getSimpleName()
                                : e.getMessage()),
                        HttpStatus.BAD_GATEWAY
                );
            }
        }
        return out;
    }
}
