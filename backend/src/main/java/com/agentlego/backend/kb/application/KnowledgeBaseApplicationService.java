package com.agentlego.backend.kb.application;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.kb.application.dto.*;
import com.agentlego.backend.kb.chunk.KbChunkSplitter;
import com.agentlego.backend.kb.chunk.KbChunkStrategies;
import com.agentlego.backend.kb.domain.*;
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
        return repository.listBasesWithStats().stream().map(this::toBaseDto).toList();
    }

    public KbBaseDto getBase(String id) {
        return repository.listBasesWithStats().stream()
                .filter(b -> id.equals(b.getId()))
                .findFirst()
                .map(this::toBaseDto)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "knowledge base not found", HttpStatus.NOT_FOUND));
    }

    public KbBaseDto createBase(CreateKbBaseRequest req) {
        String kbKey = req.getKbKey().trim();
        if (repository.findBaseByKbKey(kbKey).isPresent()) {
            throw new ApiException("CONFLICT", "kbKey already exists", HttpStatus.CONFLICT);
        }
        String id = repository.insertBase(kbKey, req.getName().trim(), normalizeDescription(req.getDescription()));
        return getBase(id);
    }

    public KbBaseDto updateBase(String id, UpdateKbBaseRequest req) {
        KbBaseSummary cur = repository.findBaseById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "knowledge base not found", HttpStatus.NOT_FOUND));
        String name = req.getName().trim();
        String desc = req.getDescription() != null ? normalizeDescription(req.getDescription()) : cur.getDescription();
        int n = repository.updateBase(id, name, desc);
        if (n == 0) {
            throw new ApiException("NOT_FOUND", "knowledge base not found", HttpStatus.NOT_FOUND);
        }
        return getBase(id);
    }

    public void deleteBase(String id) {
        int n = repository.deleteBase(id.trim());
        if (n == 0) {
            throw new ApiException("NOT_FOUND", "knowledge base not found", HttpStatus.NOT_FOUND);
        }
    }

    public KbDocumentPageDto listKnowledge(String baseId, int page, int pageSize) {
        String bid = baseId.trim();
        if (repository.findBaseById(bid).isEmpty()) {
            throw new ApiException("NOT_FOUND", "knowledge base not found", HttpStatus.NOT_FOUND);
        }
        int p = Math.max(1, page);
        int ps = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        long offset = (long) (p - 1) * ps;
        long total = repository.countDocumentsByBaseId(bid);
        List<KbDocumentSummaryDto> items = repository.listDocumentsByBaseId(bid, offset, ps).stream()
                .map(this::toDocumentDto)
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
            throw new ApiException("NOT_FOUND", "knowledge base not found", HttpStatus.NOT_FOUND);
        }
        validateChunkParams(req);
        String contentFormat = normalizeAndValidateContentFormat(req.getContentFormat());
        String chunkStrategy = normalizeChunkStrategy(req.getChunkStrategy());
        String richStored = req.getContent();
        String plainForChunks = resolvePlainForChunking(richStored, contentFormat);
        if (plainForChunks.isEmpty()) {
            throw new ApiException("VALIDATION_ERROR", "content is empty (no visible text after markdown/html processing)", HttpStatus.BAD_REQUEST);
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
                .orElseThrow(() -> new ApiException("NOT_FOUND", "knowledge base not found for kbKey", HttpStatus.NOT_FOUND));
        return ingestKnowledge(resolved, req);
    }

    public void deleteDocument(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            throw new ApiException("VALIDATION_ERROR", "documentId is required", HttpStatus.BAD_REQUEST);
        }
        int n = repository.deleteDocument(documentId.trim());
        if (n == 0) {
            throw new ApiException("NOT_FOUND", "document not found", HttpStatus.NOT_FOUND);
        }
    }

    public KbKnowledgeDetailDto getKnowledge(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            throw new ApiException("VALIDATION_ERROR", "documentId is required", HttpStatus.BAD_REQUEST);
        }
        KbDocumentDetail d = repository.findDocumentById(documentId.trim())
                .orElseThrow(() -> new ApiException("NOT_FOUND", "document not found", HttpStatus.NOT_FOUND));
        return toKnowledgeDetailDto(d);
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
        resp.setChunks(chunks.stream().map(this::toChunkDto).toList());
        return resp;
    }

    private String resolveQueryBaseId(KbQueryRequest req) {
        if (req.getBaseId() != null && !req.getBaseId().isBlank()) {
            String id = req.getBaseId().trim();
            if (repository.findBaseById(id).isEmpty()) {
                throw new ApiException("NOT_FOUND", "knowledge base not found", HttpStatus.NOT_FOUND);
            }
            return id;
        }
        if (req.getKbKey() != null && !req.getKbKey().isBlank()) {
            return repository.findBaseByKbKey(req.getKbKey().trim())
                    .map(KbBaseSummary::getId)
                    .orElseThrow(() -> new ApiException("NOT_FOUND", "knowledge base not found for kbKey", HttpStatus.NOT_FOUND));
        }
        throw new ApiException("VALIDATION_ERROR", "baseId or kbKey is required", HttpStatus.BAD_REQUEST);
    }

    private void validateChunkParams(CreateKnowledgeRequest req) {
        if (req.getChunkSize() <= req.getOverlap()) {
            throw new ApiException("VALIDATION_ERROR", "chunkSize must be greater than overlap", HttpStatus.BAD_REQUEST);
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

    private KbChunkDto toChunkDto(KbChunkAggregate agg) {
        KbChunkDto dto = new KbChunkDto();
        dto.setId(agg.getId());
        dto.setDocumentId(agg.getDocumentId());
        dto.setDocumentName(agg.getDocumentName());
        dto.setChunkIndex(agg.getChunkIndex());
        dto.setContent(agg.getContent());
        dto.setMetadata(agg.getMetadata());
        dto.setCreatedAt(agg.getCreatedAt());
        return dto;
    }

    private KbBaseDto toBaseDto(KbBaseSummary s) {
        KbBaseDto dto = new KbBaseDto();
        dto.setId(s.getId());
        dto.setKbKey(s.getKbKey());
        dto.setName(s.getName());
        dto.setDescription(s.getDescription());
        dto.setCreatedAt(s.getCreatedAt());
        dto.setDocumentCount(s.getDocumentCount());
        dto.setLastIngestAt(s.getLastIngestAt());
        return dto;
    }

    private KbDocumentSummaryDto toDocumentDto(KbDocumentSummary s) {
        KbDocumentSummaryDto dto = new KbDocumentSummaryDto();
        dto.setId(s.getId());
        dto.setBaseId(s.getBaseId());
        dto.setKbKey(s.getKbKey());
        dto.setName(s.getName());
        dto.setContentFormat(s.getContentFormat());
        dto.setChunkStrategy(s.getChunkStrategy());
        dto.setChunkCount(s.getChunkCount());
        dto.setCreatedAt(s.getCreatedAt());
        return dto;
    }

    private KbKnowledgeDetailDto toKnowledgeDetailDto(KbDocumentDetail d) {
        KbKnowledgeDetailDto dto = new KbKnowledgeDetailDto();
        dto.setId(d.getId());
        dto.setBaseId(d.getBaseId());
        dto.setKbKey(d.getKbKey());
        dto.setName(d.getName());
        dto.setContentRich(d.getContentRich());
        dto.setContentFormat(d.getContentFormat());
        dto.setChunkStrategy(d.getChunkStrategy());
        dto.setCreatedAt(d.getCreatedAt());
        return dto;
    }

    private String normalizeChunkStrategy(String raw) {
        if (raw == null || raw.isBlank()) {
            return KbChunkStrategies.FIXED;
        }
        String s = raw.trim().toLowerCase();
        if (!KbChunkStrategies.isKnown(s)) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "chunkStrategy must be one of: fixed, paragraph, hybrid, markdown_sections",
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
            throw new ApiException("VALIDATION_ERROR", "contentFormat plain is no longer supported; use markdown or html", HttpStatus.BAD_REQUEST);
        }
        if (!"markdown".equals(f) && !"html".equals(f)) {
            throw new ApiException("VALIDATION_ERROR", "contentFormat must be markdown or html", HttpStatus.BAD_REQUEST);
        }
        return f;
    }

}
