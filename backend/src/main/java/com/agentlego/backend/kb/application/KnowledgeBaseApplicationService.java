package com.agentlego.backend.kb.application;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.kb.application.assembler.KnowledgeBaseAssembler;
import com.agentlego.backend.kb.application.dto.*;
import com.agentlego.backend.kb.chunk.KbChunkSplitter;
import com.agentlego.backend.kb.chunk.KbChunkStrategies;
import com.agentlego.backend.kb.domain.KbBaseSummary;
import com.agentlego.backend.kb.domain.KbChunkAggregate;
import com.agentlego.backend.kb.domain.KbDocumentDetail;
import com.agentlego.backend.kb.domain.KnowledgeBaseRepository;
import com.agentlego.backend.kb.util.KbHtmlPlainText;
import com.agentlego.backend.kb.util.KbMarkdownPlainText;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库与知识（文档）分治：
 * <ul>
 *     <li><b>知识库</b>：空间元数据（名称、描述、绑定键 kbKey），可单独增删改；</li>
 *     <li><b>知识</b>：挂载在某一知识库下的文档与分片，通过 baseId 关联。</li>
 * </ul>
 */
@Service
public class KnowledgeBaseApplicationService {

    private static final String METADATA_CHUNK_SOURCE = "chunk_source";
    private static final String METADATA_CONTENT_FORMAT = "content_format";
    private static final String METADATA_CHUNK_STRATEGY = "chunk_strategy";
    private static final String CHUNK_SOURCE_API = "api";
    private static final int MAX_PAGE_SIZE = 100;

    private final KnowledgeBaseRepository repository;

    public KnowledgeBaseApplicationService(KnowledgeBaseRepository repository) {
        this.repository = repository;
    }

    private static String resolvePlainForChunking(String richStored, String contentFormat) {
        if ("html".equals(contentFormat)) {
            return KbHtmlPlainText.toPlain(richStored);
        }
        return KbMarkdownPlainText.toPlain(richStored);
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }

    public List<KbBaseDto> listBases() {
        return repository.listBasesWithStats().stream().map(KnowledgeBaseAssembler::toBaseDto).toList();
    }

    public KbBaseDto getBase(String id) {
        return repository.listBasesWithStats().stream()
                .filter(b -> id.equals(b.getId()))
                .findFirst()
                .map(KnowledgeBaseAssembler::toBaseDto)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "知识库未找到", HttpStatus.NOT_FOUND));
    }

    public KbBaseDto createBase(CreateKbBaseRequest req) {
        String kbKey = req.getKbKey().trim();
        if (repository.findBaseByKbKey(kbKey).isPresent()) {
            throw new ApiException("CONFLICT", "kbKey 已存在", HttpStatus.CONFLICT);
        }
        String id = repository.insertBase(kbKey, req.getName().trim(), normalizeDescription(req.getDescription()));
        return getBase(id);
    }

    public KbBaseDto updateBase(String id, UpdateKbBaseRequest req) {
        KbBaseSummary cur = repository.findBaseById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "知识库未找到", HttpStatus.NOT_FOUND));
        String name = req.getName().trim();
        String desc = req.getDescription() != null ? normalizeDescription(req.getDescription()) : cur.getDescription();
        int n = repository.updateBase(id, name, desc);
        if (n == 0) {
            throw new ApiException("NOT_FOUND", "知识库未找到", HttpStatus.NOT_FOUND);
        }
        return getBase(id);
    }

    public void deleteBase(String id) {
        int n = repository.deleteBase(id.trim());
        if (n == 0) {
            throw new ApiException("NOT_FOUND", "知识库未找到", HttpStatus.NOT_FOUND);
        }
    }

    public KbDocumentPageDto listKnowledge(String baseId, int page, int pageSize) {
        String bid = baseId.trim();
        if (repository.findBaseById(bid).isEmpty()) {
            throw new ApiException("NOT_FOUND", "知识库未找到", HttpStatus.NOT_FOUND);
        }
        int p = Math.max(1, page);
        int ps = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        long offset = (long) (p - 1) * ps;
        long total = repository.countDocumentsByBaseId(bid);
        List<KbDocumentSummaryDto> items = repository.listDocumentsByBaseId(bid, offset, ps).stream()
                .map(KnowledgeBaseAssembler::toDocumentDto)
                .toList();
        return KbDocumentPageDto.builder()
                .items(items)
                .total(total)
                .page(p)
                .pageSize(ps)
                .build();
    }

    public KbIngestResponse ingestKnowledge(String baseId, CreateKnowledgeRequest req) {
        String bid = baseId.trim();
        if (repository.findBaseById(bid).isEmpty()) {
            throw new ApiException("NOT_FOUND", "知识库未找到", HttpStatus.NOT_FOUND);
        }
        validateChunkParams(req);
        String contentFormat = normalizeAndValidateContentFormat(req.getContentFormat());
        String chunkStrategy = normalizeChunkStrategy(req.getChunkStrategy());
        String richStored = req.getContent();
        String plainForChunks = resolvePlainForChunking(richStored, contentFormat);
        if (plainForChunks.isEmpty()) {
            throw new ApiException("VALIDATION_ERROR", "内容为空（markdown/html 处理后无可见文本）", HttpStatus.BAD_REQUEST);
        }
        String documentId = repository.createDocument(bid, req.getName().trim(), richStored, contentFormat, chunkStrategy);
        List<KbChunkAggregate> chunks = buildChunks(
                documentId,
                chunkStrategy,
                plainForChunks,
                richStored,
                contentFormat,
                req.getChunkSize(),
                req.getOverlap()
        );
        repository.saveChunks(documentId, chunks);
        KbIngestResponse resp = new KbIngestResponse();
        resp.setDocumentId(documentId);
        resp.setChunkCount(chunks.size());
        return resp;
    }

    /**
     * 通过 kbKey 添加入库（兼容脚本 / 旧路径），内部解析为 baseId。
     */
    public KbIngestResponse ingestKnowledgeByKbKey(String kbKey, CreateKnowledgeRequest req) {
        String resolved = repository.findBaseByKbKey(kbKey.trim())
                .map(KbBaseSummary::getId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "对应 kbKey 的知识库未找到", HttpStatus.NOT_FOUND));
        return ingestKnowledge(resolved, req);
    }

    public void deleteDocument(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            throw new ApiException("VALIDATION_ERROR", "documentId 为必填", HttpStatus.BAD_REQUEST);
        }
        int n = repository.deleteDocument(documentId.trim());
        if (n == 0) {
            throw new ApiException("NOT_FOUND", "文档未找到", HttpStatus.NOT_FOUND);
        }
    }

    public KbKnowledgeDetailDto getKnowledge(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            throw new ApiException("VALIDATION_ERROR", "documentId 为必填", HttpStatus.BAD_REQUEST);
        }
        KbDocumentDetail d = repository.findDocumentById(documentId.trim())
                .orElseThrow(() -> new ApiException("NOT_FOUND", "文档未找到", HttpStatus.NOT_FOUND));
        return KnowledgeBaseAssembler.toKnowledgeDetailDto(d);
    }

    public KbQueryResponse query(KbQueryRequest req) {
        String baseId = resolveQueryBaseId(req);
        List<KbChunkAggregate> chunks = repository.queryChunksByBaseId(
                baseId,
                req.getQueryText(),
                req.getTopK(),
                req.getEmbeddingModelId()
        );
        KbQueryResponse resp = new KbQueryResponse();
        resp.setChunks(chunks.stream().map(KnowledgeBaseAssembler::toChunkDto).toList());
        return resp;
    }

    private String resolveQueryBaseId(KbQueryRequest req) {
        if (req.getBaseId() != null && !req.getBaseId().isBlank()) {
            String id = req.getBaseId().trim();
            if (repository.findBaseById(id).isEmpty()) {
                throw new ApiException("NOT_FOUND", "知识库未找到", HttpStatus.NOT_FOUND);
            }
            return id;
        }
        if (req.getKbKey() != null && !req.getKbKey().isBlank()) {
            return repository.findBaseByKbKey(req.getKbKey().trim())
                    .map(KbBaseSummary::getId)
                    .orElseThrow(() -> new ApiException("NOT_FOUND", "对应 kbKey 的知识库未找到", HttpStatus.NOT_FOUND));
        }
        throw new ApiException("VALIDATION_ERROR", "baseId 或 kbKey 为必填", HttpStatus.BAD_REQUEST);
    }

    private void validateChunkParams(CreateKnowledgeRequest req) {
        if (req.getChunkSize() <= req.getOverlap()) {
            throw new ApiException("VALIDATION_ERROR", "chunkSize 必须大于 overlap", HttpStatus.BAD_REQUEST);
        }
    }

    private List<KbChunkAggregate> buildChunks(
            String documentId,
            String chunkStrategy,
            String plainContent,
            String rawStored,
            String contentFormat,
            int chunkSize,
            int overlap
    ) {
        List<String> pieces = KbChunkSplitter.split(chunkStrategy, plainContent, rawStored, contentFormat, chunkSize, overlap);
        if (pieces.isEmpty()) {
            pieces = List.of(plainContent);
        }
        List<KbChunkAggregate> chunks = new ArrayList<>(pieces.size());
        Instant now = Instant.now();
        for (int i = 0; i < pieces.size(); i++) {
            chunks.add(buildOneChunk(documentId, i, pieces.get(i), contentFormat, chunkStrategy, now));
        }
        return chunks;
    }

    private KbChunkAggregate buildOneChunk(
            String documentId,
            int chunkIndex,
            String content,
            String contentFormat,
            String chunkStrategy,
            Instant createdAt
    ) {
        KbChunkAggregate c = new KbChunkAggregate();
        c.setId(SnowflakeIdGenerator.nextId());
        c.setDocumentId(documentId);
        c.setChunkIndex(chunkIndex);
        c.setContent(content);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put(METADATA_CHUNK_SOURCE, CHUNK_SOURCE_API);
        meta.put(METADATA_CONTENT_FORMAT, contentFormat);
        meta.put(METADATA_CHUNK_STRATEGY, chunkStrategy);
        c.setMetadata(meta);
        c.setCreatedAt(createdAt);
        return c;
    }

    private String normalizeChunkStrategy(String raw) {
        if (raw == null || raw.isBlank()) {
            return KbChunkStrategies.FIXED;
        }
        String s = raw.trim().toLowerCase();
        if (!KbChunkStrategies.isKnown(s)) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "chunkStrategy 必须是下列之一：fixed、paragraph、hybrid、markdown_sections",
                    HttpStatus.BAD_REQUEST
            );
        }
        return s;
    }

    private String normalizeAndValidateContentFormat(String raw) {
        if (raw == null || raw.isBlank()) {
            return "markdown";
        }
        String f = raw.trim().toLowerCase();
        if ("plain".equals(f)) {
            throw new ApiException("VALIDATION_ERROR", "不再支持 contentFormat=plain；请使用 markdown 或 html", HttpStatus.BAD_REQUEST);
        }
        if (!"markdown".equals(f) && !"html".equals(f)) {
            throw new ApiException("VALIDATION_ERROR", "contentFormat 必须是 markdown 或 html", HttpStatus.BAD_REQUEST);
        }
        return f;
    }

}
