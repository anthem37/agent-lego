package com.agentlego.backend.kb.application.service;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.common.Throwables;
import com.agentlego.backend.kb.domain.KbCollectionAggregate;
import com.agentlego.backend.kb.domain.KbDocumentRepository;
import com.agentlego.backend.kb.support.KbChunkExecutor;
import com.agentlego.backend.kb.support.KbChunkSlice;
import com.agentlego.backend.kb.support.KbIngestEmbeddingInputs;
import com.agentlego.backend.kb.vector.KbVectorChunkRow;
import com.agentlego.backend.kb.vector.KbVectorStore;
import com.agentlego.backend.model.support.ModelEmbeddingClient;
import com.agentlego.backend.model.support.ModelEmbeddingDimensions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 分片、嵌入、写向量库并将文档标为就绪；失败时落库错误信息。
 */
@Component
public class KbIngestFinalizeRunner {

    private final KbDocumentRepository documentRepository;
    private final KbVectorStore vectorStore;
    private final ModelEmbeddingClient embeddingClient;
    private final TransactionTemplate transactionTemplate;
    private final int maxChunksPerDocument;

    public KbIngestFinalizeRunner(
            KbDocumentRepository documentRepository,
            KbVectorStore vectorStore,
            ModelEmbeddingClient embeddingClient,
            PlatformTransactionManager transactionManager,
            @Value("${agentlego.kb.ingest.max-chunks:2000}") int maxChunksPerDocument
    ) {
        this.documentRepository = documentRepository;
        this.vectorStore = vectorStore;
        this.embeddingClient = embeddingClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.maxChunksPerDocument = maxChunksPerDocument;
    }

    public void runFinalize(
            String docId,
            KbCollectionAggregate col,
            String documentTitle,
            String bodyMarkdown,
            List<String> similarQueries
    ) {
        try {
            KbChunkExecutor executor;
            try {
                executor = KbChunkExecutor.fromStorage(col.getChunkStrategy(), col.getChunkParamsJson());
            } catch (IllegalArgumentException e) {
                transactionTemplate.executeWithoutResult(s -> documentRepository.markFailed(docId, e.getMessage()));
                throw new ApiException("VALIDATION_ERROR", e.getMessage(), HttpStatus.BAD_REQUEST);
            }
            List<KbChunkSlice> slices;
            try {
                slices = executor.chunkSlices(bodyMarkdown);
            } catch (IllegalArgumentException e) {
                transactionTemplate.executeWithoutResult(s -> documentRepository.markFailed(docId, e.getMessage()));
                throw new ApiException("VALIDATION_ERROR", e.getMessage(), HttpStatus.BAD_REQUEST);
            }
            if (slices.size() > maxChunksPerDocument) {
                String msg = "分片数量超过上限 " + maxChunksPerDocument + "，请增大分片窗口或拆分文档";
                transactionTemplate.executeWithoutResult(s -> documentRepository.markFailed(docId, msg));
                throw new ApiException("VALIDATION_ERROR", msg, HttpStatus.BAD_REQUEST);
            }
            if (slices.isEmpty()) {
                vectorStore.deleteByDocumentId(col, docId);
                transactionTemplate.executeWithoutResult(s -> documentRepository.markReady(docId));
                return;
            }
            List<String> embeddingInputs = KbIngestEmbeddingInputs.build(documentTitle, slices, similarQueries);
            List<float[]> vectors = embeddingClient.embed(col.getEmbeddingModelId(), embeddingInputs);
            if (vectors.size() != slices.size()) {
                throw new IllegalStateException("embedding 条数与分片不一致");
            }
            List<KbVectorChunkRow> rows = new ArrayList<>(slices.size());
            for (int i = 0; i < slices.size(); i++) {
                String chunkId = SnowflakeIdGenerator.nextId();
                float[] stored = ModelEmbeddingDimensions.fitToCollectionDim(vectors.get(i), col.getEmbeddingDims());
                KbChunkSlice sl = slices.get(i);
                rows.add(new KbVectorChunkRow(chunkId, docId, i, sl.content(), stored));
            }
            vectorStore.deleteByDocumentId(col, docId);
            vectorStore.upsertChunks(col, rows);
            transactionTemplate.executeWithoutResult(status -> documentRepository.markReady(docId));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            String msg = Throwables.messageOrSimpleName(e);
            transactionTemplate.executeWithoutResult(s -> documentRepository.markFailed(docId, msg));
            throw new ApiException("UPSTREAM_ERROR", "知识库写入失败：" + msg, HttpStatus.BAD_GATEWAY);
        }
    }
}
