package com.agentlego.backend.kb.infrastructure;

import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.kb.domain.KbChunkAggregate;
import com.agentlego.backend.kb.domain.KnowledgeBaseRepository;
import com.agentlego.backend.kb.infrastructure.persistence.KbChunkDO;
import com.agentlego.backend.kb.infrastructure.persistence.KbChunkMapper;
import com.agentlego.backend.kb.infrastructure.persistence.KbDocumentDO;
import com.agentlego.backend.kb.infrastructure.persistence.KbDocumentMapper;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/**
 * 知识库仓库实现（Repository Impl）。
 * <p>
 * 说明：chunk.metadataJson 的序列化/反序列化统一通过 `JsonMaps`，避免重复样板代码。
 */
@Repository
public class KnowledgeBaseRepositoryImpl implements KnowledgeBaseRepository {

    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;

    public KnowledgeBaseRepositoryImpl(KbDocumentMapper documentMapper, KbChunkMapper chunkMapper) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
    }

    @Override
    public String ensureDocument(String kbKey, String name) {
        KbDocumentDO existing = documentMapper.findByKbKey(kbKey);
        if (existing != null) {
            return existing.getId();
        }

        KbDocumentDO doc = new KbDocumentDO();
        doc.setId(SnowflakeIdGenerator.nextId());
        doc.setKbKey(kbKey);
        doc.setName(name);
        documentMapper.insert(doc);
        return doc.getId();
    }

    @Override
    public void saveChunks(String documentId, List<KbChunkAggregate> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        for (KbChunkAggregate chunk : chunks) {
            KbChunkDO row = new KbChunkDO();
            row.setId(chunk.getId());
            row.setDocumentId(documentId);
            row.setChunkIndex(chunk.getChunkIndex());
            row.setContent(chunk.getContent());
            row.setMetadataJson(JsonMaps.toJson(chunk.getMetadata()));
            chunkMapper.insertChunk(row);
        }
    }

    @Override
    public List<KbChunkAggregate> queryChunks(String kbKey, String queryText, int topK) {
        List<KbChunkDO> rows = chunkMapper.searchChunks(kbKey, queryText, topK);
        if (rows == null) {
            return Collections.emptyList();
        }
        return rows.stream().map(this::toAggregate).toList();
    }

    private KbChunkAggregate toAggregate(KbChunkDO row) {
        KbChunkAggregate agg = new KbChunkAggregate();
        agg.setId(row.getId());
        agg.setDocumentId(row.getDocumentId());
        agg.setChunkIndex(row.getChunkIndex());
        agg.setContent(row.getContent());
        agg.setMetadata(JsonMaps.parseObject(row.getMetadataJson()));
        agg.setCreatedAt(row.getCreatedAt());
        return agg;
    }
}

