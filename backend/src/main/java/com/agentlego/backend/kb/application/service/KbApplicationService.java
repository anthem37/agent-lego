package com.agentlego.backend.kb.application.service;

import com.agentlego.backend.agent.domain.AgentRepository;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.api.ApiRequires;
import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.kb.application.dto.*;
import com.agentlego.backend.kb.application.mapper.KbDtoMapper;
import com.agentlego.backend.kb.application.validation.KbDocumentValidator;
import com.agentlego.backend.kb.domain.KbCollectionAggregate;
import com.agentlego.backend.kb.domain.KbCollectionRepository;
import com.agentlego.backend.kb.domain.KbDocumentRepository;
import com.agentlego.backend.kb.domain.KbDocumentRow;
import com.agentlego.backend.kb.rag.KbRagRankedChunk;
import com.agentlego.backend.kb.support.*;
import com.agentlego.backend.kb.vector.KbVectorStore;
import com.agentlego.backend.tool.domain.ToolRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

@Service
public class KbApplicationService {

    private final KbCollectionRepository collectionRepository;
    private final KbDocumentRepository documentRepository;
    private final KbVectorStore vectorStore;
    private final KbCollectionCommandService kbCollectionCommandService;
    private final ObjectMapper objectMapper;
    private final AgentRepository agentRepository;
    private final ToolRepository toolRepository;
    private final KbDtoMapper kbDtoMapper;
    private final KbDocumentValidator documentValidator;
    private final KbRetrievePreviewAssembler retrievePreviewAssembler;
    private final KbVectorRetrieveRunner vectorRetrieveRunner;
    private final KbCollectionAccess kbCollectionAccess;
    private final KbIngestPayloadPreparer ingestPayloadPreparer;
    private final KbIngestFinalizeRunner ingestFinalizeRunner;
    private final TransactionTemplate transactionTemplate;

    public KbApplicationService(
            KbCollectionRepository collectionRepository,
            KbDocumentRepository documentRepository,
            KbVectorStore vectorStore,
            KbCollectionCommandService kbCollectionCommandService,
            ObjectMapper objectMapper,
            AgentRepository agentRepository,
            ToolRepository toolRepository,
            KbDtoMapper kbDtoMapper,
            KbDocumentValidator documentValidator,
            KbRetrievePreviewAssembler retrievePreviewAssembler,
            KbVectorRetrieveRunner vectorRetrieveRunner,
            KbCollectionAccess kbCollectionAccess,
            KbIngestPayloadPreparer ingestPayloadPreparer,
            KbIngestFinalizeRunner ingestFinalizeRunner,
            PlatformTransactionManager transactionManager
    ) {
        this.collectionRepository = collectionRepository;
        this.documentRepository = documentRepository;
        this.vectorStore = vectorStore;
        this.kbCollectionCommandService = kbCollectionCommandService;
        this.objectMapper = objectMapper;
        this.agentRepository = agentRepository;
        this.toolRepository = toolRepository;
        this.kbDtoMapper = kbDtoMapper;
        this.documentValidator = documentValidator;
        this.retrievePreviewAssembler = retrievePreviewAssembler;
        this.vectorRetrieveRunner = vectorRetrieveRunner;
        this.kbCollectionAccess = kbCollectionAccess;
        this.ingestPayloadPreparer = ingestPayloadPreparer;
        this.ingestFinalizeRunner = ingestFinalizeRunner;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public KbCollectionDto createCollection(CreateKbCollectionRequest req) {
        return kbCollectionCommandService.createCollection(req);
    }

    public List<KbCollectionDto> listCollections() {
        return collectionRepository.listAll().stream().map(kbDtoMapper::toCollectionDto).toList();
    }

    public KbCollectionDto getCollection(String id) {
        return kbDtoMapper.toCollectionDto(kbCollectionAccess.requireCollection(id));
    }

    @Transactional(readOnly = true)
    public List<KbAgentPolicySummaryDto> listAgentKbPolicySummaries() {
        return agentRepository.listKbPolicyPickerRows().stream()
                .map(row -> {
                    Map<String, Object> pol = JsonMaps.parseObject(row.knowledgeBasePolicyJson());
                    List<String> cids = KbPolicies.collectionIds(pol);
                    KbAgentPolicySummaryDto d = new KbAgentPolicySummaryDto();
                    d.setAgentId(row.id());
                    d.setAgentName(row.name() == null ? "" : row.name());
                    d.setCollectionIds(cids);
                    return d;
                })
                .toList();
    }

    public List<KbDocumentDto> listDocuments(String collectionId) {
        kbCollectionAccess.requireCollection(collectionId);
        return documentRepository.listByCollectionId(collectionId).stream()
                .map(kbDtoMapper::toDocumentListItemDto)
                .toList();
    }

    public KbDocumentDto getDocument(String collectionId, String documentId) {
        kbCollectionAccess.requireCollection(collectionId);
        return kbDtoMapper.toDocumentDto(kbCollectionAccess.requireDocumentInCollection(collectionId, documentId));
    }

    public RenderKbDocumentResponse renderDocumentBody(
            String collectionId,
            String documentId,
            RenderKbDocumentRequest req
    ) {
        kbCollectionAccess.requireCollection(collectionId);
        KbDocumentRow doc = kbCollectionAccess.requireDocumentInCollection(collectionId, documentId);
        Map<String, Object> outputs = (req == null || req.getToolOutputs() == null) ? Map.of() : req.getToolOutputs();
        String afterOutputs = KbToolPlaceholderExpander.expand(
                doc.getBody(),
                doc.getToolOutputBindingsJson(),
                outputs,
                objectMapper
        );
        List<String> linked = documentValidator.parseLinkedToolIdArray(doc.getLinkedToolIdsJson());
        String rendered = KbKnowledgeInlineToolSyntax.expandToolMentions(afterOutputs, toolRepository, linked);
        RenderKbDocumentResponse resp = new RenderKbDocumentResponse();
        resp.setRenderedBody(rendered);
        return resp;
    }

    public KbDocumentValidationResponse validateDocument(String collectionId, String documentId) {
        kbCollectionAccess.requireCollection(collectionId);
        return documentValidator.validateDocumentRow(
                kbCollectionAccess.requireDocumentInCollection(collectionId, documentId));
    }

    @Transactional(readOnly = true)
    public KbCollectionDocumentsValidationResponse validateCollectionDocuments(
            String collectionId,
            KbValidateCollectionDocumentsRequest req
    ) {
        KbCollectionAggregate col = kbCollectionAccess.requireCollection(collectionId);
        boolean includeIssues = req != null && Boolean.TRUE.equals(req.getIncludeIssues());
        List<KbDocumentRow> rows = documentRepository.listByCollectionId(collectionId);
        return documentValidator.validateCollectionDocuments(col, rows, includeIssues);
    }

    public KbRetrievePreviewResponse previewRetrieve(String collectionId, KbRetrievePreviewRequest req) {
        KbCollectionAggregate col = kbCollectionAccess.requireCollection(collectionId);
        if (req == null) {
            throw new ApiException("VALIDATION_ERROR", "query 不能为空", HttpStatus.BAD_REQUEST);
        }
        String q = ApiRequires.nonBlankTrimmed(req.getQuery(), "query");
        int topK = KbLimits.clampPreviewTopK(req.getTopK());
        double th = req.getScoreThreshold() != null ? req.getScoreThreshold() : 0.25d;
        boolean renderSnippets = Boolean.TRUE.equals(req.getRenderSnippets());
        List<KbRagRankedChunk> ranked = vectorRetrieveRunner.searchRanked(
                List.of(col), col.getEmbeddingModelId(), q, topK, th);
        return retrievePreviewAssembler.assemble(
                q,
                ranked,
                renderSnippets,
                KbMultiRetrievePreviewRules.indexCollectionsById(List.of(col)));
    }

    public KbRetrievePreviewResponse previewRetrieveMulti(KbMultiRetrievePreviewRequest req) {
        List<String> ids = KbMultiRetrievePreviewRules.normalizeCollectionIds(req);
        List<KbCollectionAggregate> cols =
                KbMultiRetrievePreviewRules.loadAllSameEmbeddingOrThrow(collectionRepository, ids);
        String q = ApiRequires.nonBlankTrimmed(req.getQuery(), "query");
        int topK = KbLimits.clampPreviewTopK(req.getTopK());
        double th = req.getScoreThreshold() != null ? req.getScoreThreshold() : 0.25d;
        boolean renderSnippets = Boolean.TRUE.equals(req.getRenderSnippets());
        String expectedModel = cols.get(0).getEmbeddingModelId();
        List<KbRagRankedChunk> ranked = vectorRetrieveRunner.searchRanked(cols, expectedModel, q, topK, th);
        return retrievePreviewAssembler.assemble(
                q,
                ranked,
                renderSnippets,
                KbMultiRetrievePreviewRules.indexCollectionsById(cols));
    }

    public KbDocumentDto ingestTextDocument(String collectionId, IngestKbDocumentRequest req) {
        KbCollectionAggregate col = kbCollectionAccess.requireCollection(collectionId);
        KbPreparedIngestPayload p = ingestPayloadPreparer.prepare(req);
        String docId = SnowflakeIdGenerator.nextId();
        transactionTemplate.executeWithoutResult(status ->
                documentRepository.insertPending(
                        docId,
                        collectionId,
                        p.title(),
                        p.markdownBody(),
                        p.bodyRichToStore(),
                        p.linkedToolIdsJson(),
                        p.toolOutputBindingsJson(),
                        p.similarQueriesJson()
                ));
        ingestFinalizeRunner.runFinalize(docId, col, p.title(), p.markdownBody(), p.similarQueriesNormalized());
        return documentDtoOrThrow(docId);
    }

    public KbDocumentDto updateTextDocument(String collectionId, String documentId, IngestKbDocumentRequest req) {
        KbCollectionAggregate col = kbCollectionAccess.requireCollection(collectionId);
        kbCollectionAccess.requireDocumentInCollection(collectionId, documentId);
        KbPreparedIngestPayload p = ingestPayloadPreparer.prepare(req);
        transactionTemplate.executeWithoutResult(status -> documentRepository.updateReingest(
                documentId,
                p.title(),
                p.markdownBody(),
                p.bodyRichToStore(),
                p.linkedToolIdsJson(),
                p.toolOutputBindingsJson(),
                p.similarQueriesJson()
        ));
        ingestFinalizeRunner.runFinalize(documentId, col, p.title(), p.markdownBody(), p.similarQueriesNormalized());
        return documentDtoOrThrow(documentId);
    }

    private KbDocumentDto documentDtoOrThrow(String docId) {
        return documentRepository.findById(docId)
                .map(kbDtoMapper::toDocumentDto)
                .orElseThrow(() -> new IllegalStateException("文档写入后读取失败：" + docId));
    }

    public KbCollectionDeleteResult deleteCollection(String id) {
        KbCollectionAggregate col = kbCollectionAccess.requireCollection(id);
        KbCollectionDeleteResult result = transactionTemplate.execute(status -> {
            int updated = stripKbCollectionFromAgents(id);
            collectionRepository.deleteById(id);
            return new KbCollectionDeleteResult(updated);
        });
        vectorStore.dropPhysicalCollection(col);
        return result;
    }

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
        KbCollectionAggregate col = kbCollectionAccess.requireCollection(collectionId);
        kbCollectionAccess.requireDocumentInCollection(collectionId, documentId);
        vectorStore.deleteByDocumentId(col, documentId);
        documentRepository.deleteById(documentId);
    }
}
