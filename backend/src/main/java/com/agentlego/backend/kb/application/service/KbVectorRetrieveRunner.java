package com.agentlego.backend.kb.application.service;

import com.agentlego.backend.kb.domain.KbCollectionAggregate;
import com.agentlego.backend.kb.rag.KbRagKnowledgeFactory;
import com.agentlego.backend.kb.rag.KbRagRankedChunk;
import com.agentlego.backend.kb.rag.KbVectorRetrieveEngine;
import com.agentlego.backend.kb.vector.KbVectorStore;
import com.agentlego.backend.model.support.ModelEmbeddingClient;
import io.agentscope.core.rag.model.RetrieveConfig;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 控制台召回预览与 RAG 共用：多集合并入候选后向量检索（{@link KbVectorRetrieveEngine}）。
 */
@Component
public class KbVectorRetrieveRunner {

    private final ModelEmbeddingClient embeddingClient;
    private final KbVectorStore vectorStore;

    public KbVectorRetrieveRunner(ModelEmbeddingClient embeddingClient, KbVectorStore vectorStore) {
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
    }

    public List<KbRagRankedChunk> searchRanked(
            List<KbCollectionAggregate> cols,
            String embeddingModelId,
            String query,
            int topK,
            double scoreThreshold
    ) {
        KbVectorRetrieveEngine engine = new KbVectorRetrieveEngine(
                cols,
                embeddingModelId,
                embeddingClient,
                vectorStore,
                KbRagKnowledgeFactory.DEFAULT_CANDIDATE_LIMIT
        );
        return engine.search(
                query,
                RetrieveConfig.builder().limit(topK).scoreThreshold(scoreThreshold).build()
        );
    }
}
