package com.agentlego.backend.kb.application.service;

import com.agentlego.backend.agent.domain.AgentRepository;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.api.ApiRequires;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.common.Throwables;
import com.agentlego.backend.kb.application.dto.*;
import com.agentlego.backend.kb.application.mapper.KbDtoMapper;
import com.agentlego.backend.kb.domain.*;
import com.agentlego.backend.kb.support.*;
import com.agentlego.backend.model.domain.ModelAggregate;
import com.agentlego.backend.model.domain.ModelRepository;
import com.agentlego.backend.model.support.ModelEmbeddingClient;
import com.agentlego.backend.model.support.ModelEmbeddingDimensions;
import com.agentlego.backend.tool.domain.ToolRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;

@Service
public class KbApplicationService {

    private static final int MAX_COLLECTION_NAME_CHARS = 256;
    private static final int MAX_DOCUMENT_TITLE_CHARS = 512;
    private static final int MAX_EMBEDDING_INPUT_CHARS = 8000;
    private static final ObjectMapper KB_OBJECT_MAPPER = new ObjectMapper();

    private final KbCollectionRepository collectionRepository;
    private final KbDocumentRepository documentRepository;
    private final KbChunkRepository chunkRepository;
    private final ModelRepository modelRepository;
    private final ModelEmbeddingClient embeddingClient;
    private final AgentRepository agentRepository;
    private final ToolRepository toolRepository;
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
            ToolRepository toolRepository,
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
        this.toolRepository = toolRepository;
        this.kbDtoMapper = kbDtoMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.maxDocumentChars = maxDocumentChars;
        this.maxChunksPerDocument = maxChunksPerDocument;
    }

    private static List<String> buildEmbeddingInputs(String documentTitle, List<KbChunkSlice> slices, List<String> similarQueries) {
        List<String> lines = normalizeSimilarQueries(similarQueries);
        String suffix = lines.isEmpty() ? "" : "\n\n相似问:\n" + String.join("\n", lines);
        String titlePrefix = "";
        if (documentTitle != null && !documentTitle.isBlank()) {
            titlePrefix = "文档标题: " + documentTitle.trim() + "\n\n";
        }
        List<String> out = new ArrayList<>();
        for (KbChunkSlice s : slices) {
            String emb = titlePrefix + s.embeddingText() + suffix;
            if (emb.length() > MAX_EMBEDDING_INPUT_CHARS) {
                emb = emb.substring(0, MAX_EMBEDDING_INPUT_CHARS);
            }
            out.add(emb);
        }
        return out;
    }

    private static List<String> normalizeSimilarQueries(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String s : raw) {
            if (s == null) {
                continue;
            }
            String t = s.trim();
            if (!t.isEmpty() && t.length() <= 512) {
                out.add(t);
            }
            if (out.size() >= 32) {
                break;
            }
        }
        return out;
    }

    private static String chunkMetadataJson(Map<String, Object> metadata) {
        try {
            if (metadata == null || metadata.isEmpty()) {
                return "{}";
            }
            return KB_OBJECT_MAPPER.writeValueAsString(metadata);
        } catch (Exception e) {
            return "{}";
        }
    }

    public KbCollectionDto createCollection(CreateKbCollectionRequest req) {
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
        KbChunkStrategyKind st;
        try {
            st = KbChunkStrategyKind.fromApi(req.getChunkStrategy());
        } catch (IllegalArgumentException e) {
            throw new ApiException("VALIDATION_ERROR", e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        String paramsJson;
        try {
            paramsJson = KbChunkExecutor.normalizeParamsJson(st, req.getChunkParams());
        } catch (IllegalArgumentException e) {
            throw new ApiException("VALIDATION_ERROR", e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        agg.setChunkStrategy(st.name());
        agg.setChunkParamsJson(paramsJson);
        Instant now = Instant.now();
        agg.setCreatedAt(now);
        agg.setUpdatedAt(now);
        collectionRepository.save(agg);
        return kbDtoMapper.toCollectionDto(agg);
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
                .map(kbDtoMapper::toDocumentListItemDto)
                .toList();
    }

    public KbDocumentDto getDocument(String collectionId, String documentId) {
        collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "知识库集合未找到", HttpStatus.NOT_FOUND));
        KbDocumentRow doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "文档未找到", HttpStatus.NOT_FOUND));
        if (!collectionId.equals(doc.getCollectionId())) {
            throw new ApiException("VALIDATION_ERROR", "文档不属于该集合", HttpStatus.BAD_REQUEST);
        }
        return kbDtoMapper.toDocumentDto(doc);
    }

    /**
     * 按<strong>文档级</strong>{@code tool_output_bindings} 替换 {@code {{placeholder}}}，再将 {@code {{tool:…}}} 展开为工具展示名（不入库）。
     */
    public RenderKbDocumentResponse renderDocumentBody(
            String collectionId,
            String documentId,
            RenderKbDocumentRequest req
    ) {
        collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "知识库集合未找到", HttpStatus.NOT_FOUND));
        KbDocumentRow doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "文档未找到", HttpStatus.NOT_FOUND));
        if (!collectionId.equals(doc.getCollectionId())) {
            throw new ApiException("VALIDATION_ERROR", "文档不属于该集合", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> outputs = (req == null || req.getToolOutputs() == null) ? Map.of() : req.getToolOutputs();
        String afterOutputs = KbToolPlaceholderExpander.expand(
                doc.getBody(),
                doc.getToolOutputBindingsJson(),
                outputs,
                KB_OBJECT_MAPPER
        );
        List<String> linked = parseLinkedToolIdArray(doc.getLinkedToolIdsJson());
        String rendered = KbKnowledgeInlineToolSyntax.expandToolMentions(afterOutputs, toolRepository, linked);
        RenderKbDocumentResponse resp = new RenderKbDocumentResponse();
        resp.setRenderedBody(rendered);
        return resp;
    }

    private void validateKbDocumentToolLinks(String linkedIdsJson, String bindingsJson) {
        List<String> linked = parseLinkedToolIdArray(linkedIdsJson);
        Set<String> linkedLc = new HashSet<>();
        for (String id : linked) {
            linkedLc.add(id.toLowerCase(Locale.ROOT));
            toolRepository.findById(id)
                    .orElseThrow(() -> new ApiException("NOT_FOUND", "关联工具未找到: " + id, HttpStatus.NOT_FOUND));
        }
        try {
            JsonNode root = KB_OBJECT_MAPPER.readTree(bindingsJson);
            JsonNode mappings = root.get("mappings");
            if (mappings != null && mappings.isArray()) {
                for (JsonNode m : mappings) {
                    if (m == null || !m.isObject()) {
                        continue;
                    }
                    String tid = m.path("toolId").asText("").trim();
                    if (tid.isEmpty()) {
                        continue;
                    }
                    toolRepository.findById(tid)
                            .orElseThrow(() -> new ApiException("NOT_FOUND", "绑定中的工具未找到: " + tid, HttpStatus.NOT_FOUND));
                    if (!linkedLc.contains(tid.toLowerCase(Locale.ROOT))) {
                        throw new ApiException(
                                "VALIDATION_ERROR",
                                "toolOutputBindings 中的 toolId 必须出现在 linkedToolIds 中: " + tid,
                                HttpStatus.BAD_REQUEST
                        );
                    }
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("VALIDATION_ERROR", "toolOutputBindings 无效", HttpStatus.BAD_REQUEST);
        }
    }

    private List<String> parseLinkedToolIdArray(String json) {
        try {
            JsonNode n = KB_OBJECT_MAPPER.readTree(json);
            List<String> out = new ArrayList<>();
            if (n.isArray()) {
                for (JsonNode x : n) {
                    if (x != null && x.isTextual()) {
                        String t = x.asText().trim();
                        if (!t.isEmpty()) {
                            out.add(t);
                        }
                    }
                }
            }
            return out;
        } catch (Exception e) {
            throw new ApiException("VALIDATION_ERROR", "linkedToolIds JSON 无效", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 正文中的 {@code {{tool:…}}}：推荐为运行时 {@code name}，兼容纯数字工具 ID；须能对应到本条 {@code linkedToolIds} 中的工具。
     */
    private void validateBodyInlineToolMentions(String body, String linkedIdsJson) {
        List<String> linked = parseLinkedToolIdArray(linkedIdsJson);
        List<String> bad = KbKnowledgeInlineToolSyntax.validateMentionTokensAgainstLinked(body, linked, toolRepository);
        if (!bad.isEmpty()) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "正文 {{tool:…}} 须引用本条已绑定的工具：推荐写运行时名称 name（如 {{tool:order_query}}）；纯数字为工具 ID（兼容）。无法解析："
                            + String.join("、", bad),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * 写入路径：先短事务落库 PENDING，再在事务外调用 embedding（避免长事务锁表），最后单事务批量写分片并标记 READY。
     * 失败时单独事务标记 FAILED。
     */
    public KbDocumentDto ingestTextDocument(String collectionId, IngestKbDocumentRequest req) {
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

        String linkedJson;
        String bindingsJson;
        try {
            linkedJson = KbDocumentToolBindings.normalizeLinkedToolIdsJson(req.getLinkedToolIds());
            bindingsJson = KbDocumentToolBindings.normalizeBindingsJson(req.getToolOutputBindings(), KB_OBJECT_MAPPER);
        } catch (IllegalArgumentException e) {
            throw new ApiException("VALIDATION_ERROR", e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        validateKbDocumentToolLinks(linkedJson, bindingsJson);
        validateBodyInlineToolMentions(body, linkedJson);

        String docId = SnowflakeIdGenerator.nextId();
        transactionTemplate.executeWithoutResult(status ->
                documentRepository.insertPending(docId, collectionId, title, body, linkedJson, bindingsJson));

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
                slices = executor.chunkSlices(body);
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
                transactionTemplate.executeWithoutResult(s -> documentRepository.markReady(docId));
                return documentDtoOrThrow(docId);
            }
            List<String> embeddingInputs = buildEmbeddingInputs(title, slices, req.getSimilarQueries());
            List<float[]> vectors = embeddingClient.embed(col.getEmbeddingModelId(), embeddingInputs);
            if (vectors.size() != slices.size()) {
                throw new IllegalStateException("embedding 条数与分片不一致");
            }
            transactionTemplate.executeWithoutResult(status -> {
                for (int i = 0; i < slices.size(); i++) {
                    String chunkId = SnowflakeIdGenerator.nextId();
                    float[] stored = ModelEmbeddingDimensions.padForPgStorage(vectors.get(i));
                    KbChunkSlice sl = slices.get(i);
                    String metaJson = chunkMetadataJson(sl.metadata());
                    chunkRepository.insert(
                            chunkId,
                            docId,
                            collectionId,
                            i,
                            sl.content(),
                            embeddingInputs.get(i),
                            metaJson,
                            stored
                    );
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
        return documentDtoOrThrow(docId);
    }

    private KbDocumentDto documentDtoOrThrow(String docId) {
        return documentRepository.findById(docId)
                .map(kbDtoMapper::toDocumentDto)
                .orElseThrow(() -> new IllegalStateException("文档写入后读取失败：" + docId));
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
