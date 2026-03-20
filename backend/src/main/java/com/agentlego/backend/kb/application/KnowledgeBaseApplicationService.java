package com.agentlego.backend.kb.application;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.kb.application.dto.*;
import com.agentlego.backend.kb.domain.KbChunkAggregate;
import com.agentlego.backend.kb.domain.KnowledgeBaseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 知识库应用服务（Application Service）。
 * <p>
 * 当前实现说明：
 * - ingest：将文档内容按 chunkSize/overlap 分片后落库；
 * - query：委托 repository 做检索（当前为 ilike 占位实现，后续可替换为 pgvector）。
 */
@Service
public class KnowledgeBaseApplicationService {
    private static final String METADATA_CHUNK_SOURCE = "chunk_source";
    private static final String CHUNK_SOURCE_API = "api";

    /**
     * 知识库仓库（读写 documents/chunks）。
     */
    private final KnowledgeBaseRepository repository;

    public KnowledgeBaseApplicationService(KnowledgeBaseRepository repository) {
        this.repository = repository;
    }

    public KbIngestResponse ingest(CreateKbDocumentRequest req) {
        validateIngestRequest(req);
        String documentId = repository.ensureDocument(req.getKbKey(), req.getName());
        List<KbChunkAggregate> chunks = buildChunks(documentId, req);
        repository.saveChunks(documentId, chunks);
        KbIngestResponse resp = new KbIngestResponse();
        resp.setDocumentId(documentId);
        return resp;
    }

    public KbQueryResponse query(KbQueryRequest req) {
        List<KbChunkAggregate> chunks = repository.queryChunks(req.getKbKey(), req.getQueryText(), req.getTopK());
        KbQueryResponse resp = new KbQueryResponse();
        resp.setChunks(chunks.stream().map(this::toDto).toList());
        return resp;
    }

    /**
     * ingest 参数校验。
     * <p>
     * 注意：overlap 必须小于 chunkSize，否则 step<=0 会导致死循环或重复分片。
     */
    private void validateIngestRequest(CreateKbDocumentRequest req) {
        if (req.getChunkSize() <= req.getOverlap()) {
            throw new ApiException("VALIDATION_ERROR", "chunkSize must be greater than overlap", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 根据文档内容生成 chunk 列表（含 index、metadata、createdAt）。
     */
    private List<KbChunkAggregate> buildChunks(String documentId, CreateKbDocumentRequest req) {
        List<String> pieces = split(req.getContent(), req.getChunkSize(), req.getOverlap());
        List<KbChunkAggregate> chunks = new ArrayList<>(pieces.size());
        Instant now = Instant.now();
        for (int i = 0; i < pieces.size(); i++) {
            chunks.add(buildOneChunk(documentId, i, pieces.get(i), now));
        }
        return chunks;
    }

    private KbChunkAggregate buildOneChunk(String documentId, int chunkIndex, String content, Instant createdAt) {
        KbChunkAggregate c = new KbChunkAggregate();
        c.setId(SnowflakeIdGenerator.nextId());
        c.setDocumentId(documentId);
        c.setChunkIndex(chunkIndex);
        c.setContent(content);
        c.setMetadata(Map.of(METADATA_CHUNK_SOURCE, CHUNK_SOURCE_API));
        c.setCreatedAt(createdAt);
        return c;
    }

    private KbChunkDto toDto(KbChunkAggregate agg) {
        KbChunkDto dto = new KbChunkDto();
        dto.setId(agg.getId());
        dto.setDocumentId(agg.getDocumentId());
        dto.setChunkIndex(agg.getChunkIndex());
        dto.setContent(agg.getContent());
        dto.setMetadata(agg.getMetadata());
        dto.setCreatedAt(agg.getCreatedAt());
        return dto;
    }

    private List<String> split(String content, int chunkSize, int overlap) {
        if (content == null) {
            return List.of();
        }
        if (content.length() <= chunkSize) {
            return List.of(content);
        }

        int step = Math.max(1, chunkSize - overlap);

        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(content.length(), start + chunkSize);
            pieces.add(content.substring(start, end));
            if (end == content.length()) {
                break;
            }
            start += step;
        }
        return pieces;
    }
}

