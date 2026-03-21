package com.agentlego.backend.kb.application.service;

import com.agentlego.backend.agent.domain.AgentRepository;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.api.ApiRequires;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.common.Throwables;
import com.agentlego.backend.kb.application.dto.*;
import com.agentlego.backend.kb.application.mapper.KbDtoMapper;
import com.agentlego.backend.kb.domain.*;
import com.agentlego.backend.kb.support.KbPolicies;
import com.agentlego.backend.kb.support.KbTextChunker;
import com.agentlego.backend.model.domain.ModelAggregate;
import com.agentlego.backend.model.domain.ModelRepository;
import com.agentlego.backend.model.support.ModelEmbeddingClient;
import com.agentlego.backend.model.support.ModelEmbeddingDimensions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class KbApplicationService {

    private static final int MAX_COLLECTION_NAME_CHARS = 256;
    private static final int MAX_DOCUMENT_TITLE_CHARS = 512;

    private final KbCollectionRepository collectionRepository;
    private final KbDocumentRepository documentRepository;
    private final KbChunkRepository chunkRepository;
    private final ModelRepository modelRepository;
    private final ModelEmbeddingClient embeddingClient;
    private final AgentRepository agentRepository;
    private final KbDtoMapper kbDtoMapper;
    private final TransactionTemplate transactionTemplate;
    private final int maxDocumentChars;
    private final int maxChunksPerDocument;

    public KbApplicationService(
            KbCollectionRepository collectionRepository,
            KbDocumentRepository documentRepository,
            KbChunkRepository chunkRepository,
            ModelRepository modelRepository,
            ModelEmbeddingClient embeddingClient,
            AgentRepository agentRepository,
            KbDtoMapper kbDtoMapper,
            PlatformTransactionManager transactionManager,
            @Value("${agentlego.kb.ingest.max-document-chars:524288}") int maxDocumentChars,
            @Value("${agentlego.kb.ingest.max-chunks:2000}") int maxChunksPerDocument
    ) {
        this.collectionRepository = collectionRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.modelRepository = modelRepository;
        this.embeddingClient = embeddingClient;
        this.agentRepository = agentRepository;
        this.kbDtoMapper = kbDtoMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.maxDocumentChars = maxDocumentChars;
        this.maxChunksPerDocument = maxChunksPerDocument;
    }

    public String createCollection(CreateKbCollectionRequest req) {
        ApiRequires.nonBlank(req.getEmbeddingModelId(), "embeddingModelId");
        String name = req.getName() == null ? "" : req.getName().trim();
        if (name.isEmpty()) {
            throw new ApiException("VALIDATION_ERROR", "name 为必填", HttpStatus.BAD_REQUEST);
        }
        if (name.length() > MAX_COLLECTION_NAME_CHARS) {
            throw new ApiException("VALIDATION_ERROR", "name 过长（最多 " + MAX_COLLECTION_NAME_CHARS + " 字符）", HttpStatus.BAD_REQUEST);
        }

        ModelAggregate embModel = modelRepository.findById(req.getEmbeddingModelId().trim())
                .orElseThrow(() -> new ApiException("NOT_FOUND", "embedding 模型未找到", HttpStatus.NOT_FOUND));
        int outDims = ModelEmbeddingDimensions.resolveOutputDimensions(embModel);
        if (outDims > ModelEmbeddingDimensions.PGVECTOR_STORED_DIM) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "当前知识库 pgvector 列最大支持 " + ModelEmbeddingDimensions.PGVECTOR_STORED_DIM
                            + " 维，请调整模型 dimensions 配置或使用更低维输出",
                    HttpStatus.BAD_REQUEST
            );
        }

        KbCollectionAggregate agg = new KbCollectionAggregate();
        agg.setId(SnowflakeIdGenerator.nextId());
        agg.setName(name);
        agg.setDescription(req.getDescription() == null ? "" : req.getDescription().trim());
        agg.setEmbeddingModelId(req.getEmbeddingModelId().trim());
        agg.setEmbeddingDims(outDims);
        Instant now = Instant.now();
        agg.setCreatedAt(now);
        agg.setUpdatedAt(now);
        return collectionRepository.save(agg);
    }

    public List<KbCollectionDto> listCollections() {
        return collectionRepository.listAll().stream().map(kbDtoMapper::toCollectionDto).toList();
    }

    public KbCollectionDto getCollection(String id) {
        KbCollectionAggregate agg = collectionRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "知识库集合未找到", HttpStatus.NOT_FOUND));
        return kbDtoMapper.toCollectionDto(agg);
    }

    public List<KbDocumentDto> listDocuments(String collectionId) {
        collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "知识库集合未找到", HttpStatus.NOT_FOUND));
        return documentRepository.listByCollectionId(collectionId).stream()
                .map(kbDtoMapper::toDocumentDto)
                .toList();
    }

    /**
     * 写入路径：先短事务落库 PENDING，再在事务外调用 embedding（避免长事务锁表），最后单事务批量写分片并标记 READY。
     * 失败时单独事务标记 FAILED。
     */
    public String ingestTextDocument(String collectionId, IngestKbDocumentRequest req) {
        KbCollectionAggregate col = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "知识库集合未找到", HttpStatus.NOT_FOUND));

        String title = req.getTitle() == null ? "" : req.getTitle().trim();
        if (title.isEmpty()) {
            throw new ApiException("VALIDATION_ERROR", "title 不能为空", HttpStatus.BAD_REQUEST);
        }
        if (title.length() > MAX_DOCUMENT_TITLE_CHARS) {
            throw new ApiException("VALIDATION_ERROR", "title 过长（最多 " + MAX_DOCUMENT_TITLE_CHARS + " 字符）", HttpStatus.BAD_REQUEST);
        }

        String body = req.getBody() == null ? "" : req.getBody().trim();
        if (body.isEmpty()) {
            throw new ApiException("VALIDATION_ERROR", "body 不能为空", HttpStatus.BAD_REQUEST);
        }
        if (body.length() > maxDocumentChars) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "body 超过允许长度（最多 " + maxDocumentChars + " 字符），请拆分文档",
                    HttpStatus.BAD_REQUEST
            );
        }

        String docId = SnowflakeIdGenerator.nextId();
        transactionTemplate.executeWithoutResult(status ->
                documentRepository.insertPending(docId, collectionId, title, body));

        try {
            List<String> chunks = KbTextChunker.chunk(body);
            if (chunks.size() > maxChunksPerDocument) {
                String msg = "分片数量超过上限 " + maxChunksPerDocument + "，请增大分片窗口或拆分文档";
                transactionTemplate.executeWithoutResult(s -> documentRepository.markFailed(docId, msg));
                throw new ApiException("VALIDATION_ERROR", msg, HttpStatus.BAD_REQUEST);
            }
            if (chunks.isEmpty()) {
                transactionTemplate.executeWithoutResult(s -> documentRepository.markReady(docId));
                return docId;
            }
            List<float[]> vectors = embeddingClient.embed(col.getEmbeddingModelId(), chunks);
            if (vectors.size() != chunks.size()) {
                throw new IllegalStateException("embedding 条数与分片不一致");
            }
            transactionTemplate.executeWithoutResult(status -> {
                for (int i = 0; i < chunks.size(); i++) {
                    String chunkId = SnowflakeIdGenerator.nextId();
                    float[] stored = ModelEmbeddingDimensions.padForPgStorage(vectors.get(i));
                    chunkRepository.insert(chunkId, docId, collectionId, i, chunks.get(i), stored);
                }
                documentRepository.markReady(docId);
            });
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            String msg = Throwables.messageOrSimpleName(e);
            transactionTemplate.executeWithoutResult(s -> documentRepository.markFailed(docId, msg));
            throw new ApiException("UPSTREAM_ERROR", "知识库写入失败：" + msg, HttpStatus.BAD_GATEWAY);
        }
        return docId;
    }

    @Transactional
    public KbCollectionDeleteResult deleteCollection(String id) {
        collectionRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "知识库集合未找到", HttpStatus.NOT_FOUND));
        int updated = stripKbCollectionFromAgents(id);
        collectionRepository.deleteById(id);
        return new KbCollectionDeleteResult(updated);
    }

    /**
     * 从所有智能体 {@code knowledge_base_policy} 中移除该集合；若无剩余集合则策略置为 {@code {}}。
     *
     * @return 实际执行了策略写回的智能体数量
     */
    private int stripKbCollectionFromAgents(String collectionId) {
        List<String> agentIds = agentRepository.listAgentIdsReferencingKbCollection(collectionId);
        for (String agentId : agentIds) {
            Map<String, Object> current = agentRepository.findById(agentId)
                    .map(a -> a.getKnowledgeBasePolicy() == null ? Map.<String, Object>of() : a.getKnowledgeBasePolicy())
                    .orElse(Map.of());
            Map<String, Object> next = KbPolicies.withoutCollectionId(current, collectionId);
            agentRepository.updateKnowledgeBasePolicy(agentId, next);
        }
        return agentIds.size();
    }

    public void deleteDocument(String collectionId, String documentId) {
        collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "知识库集合未找到", HttpStatus.NOT_FOUND));
        KbDocumentRow doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "文档未找到", HttpStatus.NOT_FOUND));
        if (!collectionId.equals(doc.getCollectionId())) {
            throw new ApiException("VALIDATION_ERROR", "文档不属于该集合", HttpStatus.BAD_REQUEST);
        }
        documentRepository.deleteById(documentId);
    }
}
