package com.agentlego.backend.kb.rag;

import com.agentlego.backend.kb.application.KnowledgeBaseApplicationService;
import com.agentlego.backend.kb.application.dto.KbChunkDto;
import com.agentlego.backend.kb.application.dto.KbQueryRequest;
import com.agentlego.backend.kb.application.dto.KbQueryResponse;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.model.RetrieveConfig;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 实现 AgentScope {@link Knowledge}，将平台知识库查询委托给 {@link KnowledgeBaseApplicationService}。
 *
 * <p>符合业内惯例：平台 KB 通过适配器接入 AgentScope RAG 链路，Agent 可直接使用 .knowledge() 而非手写注入。
 */
public final class AgentLegoKnowledge implements Knowledge {

    private final String kbKey;
    private final String embeddingModelId;
    private final int topK;
    private final double scoreThreshold;
    private final KnowledgeBaseApplicationService kbService;

    private AgentLegoKnowledge(
            String kbKey,
            String embeddingModelId,
            int topK,
            double scoreThreshold,
            KnowledgeBaseApplicationService kbService
    ) {
        this.kbKey = Objects.requireNonNull(kbKey, "kbKey");
        this.embeddingModelId = embeddingModelId;
        this.topK = Math.max(1, Math.min(topK, 100));
        this.scoreThreshold = scoreThreshold;
        this.kbService = Objects.requireNonNull(kbService, "kbService");
    }

    public static AgentLegoKnowledge create(
            String kbKey,
            String embeddingModelId,
            int topK,
            double scoreThreshold,
            KnowledgeBaseApplicationService kbService
    ) {
        return new AgentLegoKnowledge(kbKey, embeddingModelId, topK, scoreThreshold, kbService);
    }

    @Override
    public Mono<Void> addDocuments(List<Document> documents) {
        // 平台通过独立 ingest API 管理文档，不通过 Knowledge.addDocuments 写入
        return Mono.empty().then();
    }

    @Override
    public Mono<List<Document>> retrieve(String query, RetrieveConfig config) {
        return Mono.fromCallable(() -> {
            KbQueryRequest req = new KbQueryRequest();
            req.setKbKey(kbKey);
            req.setQueryText(query);
            req.setTopK(config != null ? Math.max(1, config.getLimit()) : topK);
            if (embeddingModelId != null && !embeddingModelId.isBlank()) {
                req.setEmbeddingModelId(embeddingModelId);
            }
            KbQueryResponse resp = kbService.query(req);
            if (resp == null || resp.getChunks() == null) {
                return List.<Document>of();
            }
            return resp.getChunks().stream()
                    .map(this::toDocument)
                    .collect(Collectors.toList());
        });
    }

    private Document toDocument(KbChunkDto chunk) {
        String content = chunk.getContent() == null ? "" : chunk.getContent();
        DocumentMetadata metadata = DocumentMetadata.builder()
                .content(TextBlock.builder().text(content).build())
                .docId(chunk.getDocumentId())
                .chunkId(chunk.getId())
                .payload(chunk.getMetadata() != null ? chunk.getMetadata() : Map.of())
                .build();
        return new Document(metadata);
    }
}
