package com.agentlego.backend.kb.runtime;

import com.agentlego.backend.kb.domain.KbDocumentRepository;
import com.agentlego.backend.kb.domain.KbDocumentRow;
import com.agentlego.backend.kb.rag.KbRagRankedChunk;
import com.agentlego.backend.kb.rag.KbRagRetrieveEngine;
import com.agentlego.backend.kb.rag.KbRetrievedChunkRenderer;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.model.RetrieveConfig;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 平台知识库 → {@link Knowledge}：检索由 {@link KbRagRetrieveEngine} 完成，片段正文由 {@link KbRetrievedChunkRenderer}
 * 在注入模型前按文档绑定与会话工具出参做后处理。
 */
public final class KbVectorKnowledge implements Knowledge {

    private final KbRagRetrieveEngine retrieveEngine;
    private final KbDocumentRepository documentRepository;
    private final KbRetrievedChunkRenderer chunkRenderer;
    private final Supplier<Map<String, Object>> toolOutputsSupplier;

    public KbVectorKnowledge(
            KbRagRetrieveEngine retrieveEngine,
            KbDocumentRepository documentRepository,
            KbRetrievedChunkRenderer chunkRenderer,
            Supplier<Map<String, Object>> toolOutputsSupplier
    ) {
        this.retrieveEngine = Objects.requireNonNull(retrieveEngine, "retrieveEngine");
        this.documentRepository = Objects.requireNonNull(documentRepository, "documentRepository");
        this.chunkRenderer = Objects.requireNonNull(chunkRenderer, "chunkRenderer");
        this.toolOutputsSupplier = Objects.requireNonNull(toolOutputsSupplier, "toolOutputsSupplier");
    }

    @Override
    public Mono<Void> addDocuments(List<Document> documents) {
        return Mono.empty();
    }

    @Override
    public Mono<List<Document>> retrieve(String query, RetrieveConfig config) {
        return Mono.fromCallable(() -> doRetrieve(query, config))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private List<Document> doRetrieve(String query, RetrieveConfig config) {
        List<KbRagRankedChunk> ranked = retrieveEngine.search(query, config);
        if (ranked.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> docIds = new LinkedHashSet<>();
        for (KbRagRankedChunk c : ranked) {
            if (c.documentId() != null && !c.documentId().isBlank()) {
                docIds.add(c.documentId().trim());
            }
        }
        LinkedHashMap<String, KbDocumentRow> docById = new LinkedHashMap<>();
        if (!docIds.isEmpty()) {
            for (KbDocumentRow row : documentRepository.findByIds(new ArrayList<>(docIds))) {
                if (row != null && row.getId() != null) {
                    docById.putIfAbsent(row.getId(), row);
                }
            }
        }
        Map<String, Object> toolOutputs = toolOutputsSupplier.get();
        if (toolOutputs == null) {
            toolOutputs = Map.of();
        }

        List<Document> out = new ArrayList<>();
        for (KbRagRankedChunk c : ranked) {
            KbDocumentRow doc = c.documentId() == null || c.documentId().isBlank()
                    ? null
                    : docById.get(c.documentId().trim());
            String text = chunkRenderer.renderForModel(c.content(), doc, toolOutputs);
            DocumentMetadata meta = DocumentMetadata.builder()
                    .content(TextBlock.builder().text(text).build())
                    .docId(c.documentId())
                    .chunkId(c.chunkId())
                    .build();
            Document d = new Document(meta);
            d.setScore(c.score());
            out.add(d);
        }
        return out;
    }
}
