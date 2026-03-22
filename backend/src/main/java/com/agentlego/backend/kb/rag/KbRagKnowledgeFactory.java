package com.agentlego.backend.kb.rag;

import com.agentlego.backend.agent.domain.AgentAggregate;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.kb.domain.KbCollectionAggregate;
import com.agentlego.backend.kb.domain.KbCollectionRepository;
import com.agentlego.backend.kb.domain.KbDocumentRepository;
import com.agentlego.backend.kb.runtime.KbVectorKnowledge;
import com.agentlego.backend.kb.support.KbPolicies;
import com.agentlego.backend.kb.vector.KbVectorStore;
import com.agentlego.backend.model.support.ModelEmbeddingClient;
import io.agentscope.core.rag.Knowledge;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 根据智能体 {@code knowledge_base_policy} 装配 {@link Knowledge} 与 RAG 数值参数。
 * <p>
 * {@link KbRagSessionToolOutputs} 由单次 {@code runAgent} 创建，与带录制的 Toolkit 共享，用于 RAG 渲染时替换
 * {@code tool_field}；不实现跨请求缓存。
 */
@Component
public class KbRagKnowledgeFactory {

    /**
     * 每个物理向量集合上取的候选条数上限（用于多集合合并前的单次搜索）。
     */
    public static final int DEFAULT_CANDIDATE_LIMIT = 500;

    private final KbCollectionRepository collectionRepository;
    private final KbDocumentRepository documentRepository;
    private final KbRetrievedChunkRenderer retrievedChunkRenderer;
    private final ModelEmbeddingClient embeddingClient;
    private final KbVectorStore vectorStore;

    public KbRagKnowledgeFactory(
            KbCollectionRepository collectionRepository,
            KbDocumentRepository documentRepository,
            KbRetrievedChunkRenderer retrievedChunkRenderer,
            ModelEmbeddingClient embeddingClient,
            KbVectorStore vectorStore
    ) {
        this.collectionRepository = collectionRepository;
        this.documentRepository = documentRepository;
        this.retrievedChunkRenderer = retrievedChunkRenderer;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
    }

    public Optional<KnowledgeBinding> resolve(AgentAggregate agent) {
        return resolve(agent, null);
    }

    public Optional<KnowledgeBinding> resolve(AgentAggregate agent, KbRagSessionToolOutputs sessionToolOutputs) {
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

        KbVectorRetrieveEngine engine = new KbVectorRetrieveEngine(
                cols,
                embeddingModelId,
                embeddingClient,
                vectorStore,
                DEFAULT_CANDIDATE_LIMIT
        );
        Supplier<Map<String, Object>> outputs =
                sessionToolOutputs == null ? () -> Map.of() : sessionToolOutputs::forExpansion;
        Knowledge knowledge = new KbVectorKnowledge(engine, documentRepository, retrievedChunkRenderer, outputs);
        int topK = KbPolicies.topK(agent.getKnowledgeBasePolicy(), 5);
        double threshold = KbPolicies.scoreThreshold(agent.getKnowledgeBasePolicy(), 0.25d);
        return Optional.of(new KnowledgeBinding(knowledge, topK, threshold));
    }

    public record KnowledgeBinding(Knowledge knowledge, int topK, double scoreThreshold) {
    }
}
