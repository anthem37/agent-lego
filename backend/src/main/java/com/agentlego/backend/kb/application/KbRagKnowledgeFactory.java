package com.agentlego.backend.kb.application;

import com.agentlego.backend.agent.domain.AgentAggregate;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.kb.domain.KbChunkRepository;
import com.agentlego.backend.kb.domain.KbCollectionAggregate;
import com.agentlego.backend.kb.domain.KbCollectionRepository;
import com.agentlego.backend.kb.runtime.KbVectorKnowledge;
import com.agentlego.backend.kb.support.KbPolicies;
import com.agentlego.backend.model.support.ModelEmbeddingClient;
import io.agentscope.core.rag.Knowledge;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 根据智能体 {@code knowledge_base_policy} 装配 {@link Knowledge} 与 RAG 数值参数。
 */
@Component
public class KbRagKnowledgeFactory {

    /**
     * pgvector 检索候选条数（按余弦距离排序后 LIMIT）；略大于 topK 以提高召回。
     */
    public static final int DEFAULT_CANDIDATE_LIMIT = 500;

    private final KbCollectionRepository collectionRepository;
    private final KbChunkRepository chunkRepository;
    private final ModelEmbeddingClient embeddingClient;

    public KbRagKnowledgeFactory(
            KbCollectionRepository collectionRepository,
            KbChunkRepository chunkRepository,
            ModelEmbeddingClient embeddingClient
    ) {
        this.collectionRepository = collectionRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingClient = embeddingClient;
    }

    public Optional<KnowledgeBinding> resolve(AgentAggregate agent) {
        List<String> ids = KbPolicies.collectionIds(agent.getKnowledgeBasePolicy());
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        List<KbCollectionAggregate> cols = collectionRepository.findByIds(ids);
        if (cols.size() != ids.size()) {
            throw new ApiException("NOT_FOUND", "知识库集合未找到或不完整", HttpStatus.NOT_FOUND);
        }
        String expectedModel = cols.get(0).getEmbeddingModelId();
        for (KbCollectionAggregate c : cols) {
            if (!expectedModel.equals(c.getEmbeddingModelId())) {
                throw new ApiException(
                        "VALIDATION_ERROR",
                        "knowledge_base_policy 中的集合必须使用相同的 embedding_model_id",
                        HttpStatus.BAD_REQUEST
                );
            }
        }
        String override = KbPolicies.embeddingModelOverride(agent.getKnowledgeBasePolicy());
        String embeddingModelId = override.isBlank() ? expectedModel : override;

        Knowledge knowledge = new KbVectorKnowledge(
                ids,
                embeddingModelId,
                chunkRepository,
                embeddingClient,
                DEFAULT_CANDIDATE_LIMIT
        );
        int topK = KbPolicies.topK(agent.getKnowledgeBasePolicy(), 5);
        double threshold = KbPolicies.scoreThreshold(agent.getKnowledgeBasePolicy(), 0.25d);
        return Optional.of(new KnowledgeBinding(knowledge, topK, threshold));
    }

    public record KnowledgeBinding(Knowledge knowledge, int topK, double scoreThreshold) {
    }
}
