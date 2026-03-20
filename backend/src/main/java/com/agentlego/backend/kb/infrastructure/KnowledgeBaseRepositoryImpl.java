package com.agentlego.backend.kb.infrastructure;

import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.kb.domain.KbBaseSummary;
import com.agentlego.backend.kb.domain.KbChunkAggregate;
import com.agentlego.backend.kb.domain.KbDocumentDetail;
import com.agentlego.backend.kb.domain.KbDocumentSummary;
import com.agentlego.backend.kb.chunk.KbChunkStrategies;
import com.agentlego.backend.kb.domain.KnowledgeBaseRepository;
import com.agentlego.backend.kb.infrastructure.persistence.KbBaseDO;
import com.agentlego.backend.kb.infrastructure.persistence.KbBaseMapper;
import com.agentlego.backend.kb.infrastructure.persistence.KbChunkDO;
import com.agentlego.backend.kb.infrastructure.persistence.KbChunkMapper;
import com.agentlego.backend.kb.infrastructure.persistence.KbDocumentDO;
import com.agentlego.backend.kb.infrastructure.persistence.KbDocumentMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class KnowledgeBaseRepositoryImpl implements KnowledgeBaseRepository {

    private final KbBaseMapper baseMapper;
    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;

    public KnowledgeBaseRepositoryImpl(KbBaseMapper baseMapper, KbDocumentMapper documentMapper, KbChunkMapper chunkMapper) {
        this.baseMapper = baseMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
    }

    @Override
    public String insertBase(String kbKey, String name, String description) {
        KbBaseDO row = new KbBaseDO();
        row.setId(SnowflakeIdGenerator.nextId());
        row.setKbKey(kbKey);
        row.setName(name);
        row.setDescription(description);
        baseMapper.insert(row);
        return row.getId();
    }

    @Override
    public int updateBase(String id, String name, String description) {
        KbBaseDO row = new KbBaseDO();
        row.setId(id);
        row.setName(name);
        row.setDescription(description);
        return baseMapper.updateById(row);
    }

    @Override
    public int deleteBase(String id) {
        return baseMapper.deleteById(id);
    }

    @Override
    public Optional<KbBaseSummary> findBaseById(String id) {
        KbBaseDO row = baseMapper.findById(id);
        return row == null ? Optional.empty() : Optional.of(toBaseSummary(row, 0, null));
    }

    @Override
    public Optional<KbBaseSummary> findBaseByKbKey(String kbKey) {
        KbBaseDO row = baseMapper.findByKbKey(kbKey);
        return row == null ? Optional.empty() : Optional.of(toBaseSummary(row, 0, null));
    }

    @Override
    public List<KbBaseSummary> listBasesWithStats() {
        List<KbBaseDO> rows = baseMapper.listAllWithStats();
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream()
                .map(r -> toBaseSummary(
                        r,
                        r.getDocumentCount() == null ? 0L : r.getDocumentCount(),
                        r.getLastIngestAt()
                ))
                .toList();
    }

    @Override
    public String createDocument(String baseId, String name, String contentRich, String contentFormat, String chunkStrategy) {
        KbDocumentDO doc = new KbDocumentDO();
        doc.setId(SnowflakeIdGenerator.nextId());
        doc.setBaseId(baseId);
        doc.setName(name);
        doc.setContentRich(contentRich);
        doc.setContentFormat(contentFormat);
        doc.setChunkStrategy(chunkStrategy);
        documentMapper.insert(doc);
        return doc.getId();
    }

    @Override
    public int deleteDocument(String documentId) {
        return documentMapper.deleteById(documentId);
    }

    @Override
    public List<KbDocumentSummary> listDocumentsByBaseId(String baseId, long offset, int limit) {
        List<KbDocumentDO> rows = documentMapper.listByBaseIdPaged(baseId, offset, limit);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream().map(this::toDocumentSummary).toList();
    }

    @Override
    public long countDocumentsByBaseId(String baseId) {
        return documentMapper.countByBaseId(baseId);
    }

    @Override
    public Optional<KbDocumentDetail> findDocumentById(String documentId) {
        KbDocumentDO row = documentMapper.findById(documentId);
        if (row == null) {
            return Optional.empty();
        }
        KbDocumentDetail d = new KbDocumentDetail();
        d.setId(row.getId());
        d.setBaseId(row.getBaseId());
        d.setKbKey(row.getKbKey());
        d.setName(row.getName());
        d.setContentRich(row.getContentRich());
        d.setContentFormat(row.getContentFormat());
        d.setChunkStrategy(row.getChunkStrategy() == null || row.getChunkStrategy().isBlank()
                ? KbChunkStrategies.FIXED
                : row.getChunkStrategy());
        d.setCreatedAt(row.getCreatedAt());
        return Optional.of(d);
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
    public List<KbChunkAggregate> queryChunksByBaseId(String baseId, String queryText, int topK) {
        List<KbChunkDO> rows = chunkMapper.searchChunks(baseId, queryText, topK);
        if (rows == null) {
            return Collections.emptyList();
        }
        return rows.stream().map(this::toAggregate).toList();
    }

    private KbBaseSummary toBaseSummary(KbBaseDO row, long documentCount, Instant lastIngestAt) {
        KbBaseSummary s = new KbBaseSummary();
        s.setId(row.getId());
        s.setKbKey(row.getKbKey());
        s.setName(row.getName());
        s.setDescription(row.getDescription());
        s.setCreatedAt(row.getCreatedAt());
        s.setDocumentCount(documentCount);
        s.setLastIngestAt(lastIngestAt);
        return s;
    }

    private KbDocumentSummary toDocumentSummary(KbDocumentDO row) {
        KbDocumentSummary s = new KbDocumentSummary();
        s.setId(row.getId());
        s.setBaseId(row.getBaseId());
        s.setKbKey(row.getKbKey());
        s.setName(row.getName());
        s.setContentFormat(row.getContentFormat() == null ? "markdown" : row.getContentFormat());
        s.setChunkStrategy(row.getChunkStrategy() == null || row.getChunkStrategy().isBlank()
                ? KbChunkStrategies.FIXED
                : row.getChunkStrategy());
        s.setCreatedAt(row.getCreatedAt());
        s.setChunkCount(row.getChunkCount() == null ? 0 : row.getChunkCount());
        return s;
    }

    private KbChunkAggregate toAggregate(KbChunkDO row) {
        KbChunkAggregate agg = new KbChunkAggregate();
        agg.setId(row.getId());
        agg.setDocumentId(row.getDocumentId());
        agg.setChunkIndex(row.getChunkIndex());
        agg.setContent(row.getContent());
        agg.setMetadata(JsonMaps.parseObject(row.getMetadataJson()));
        agg.setCreatedAt(row.getCreatedAt());
        agg.setDocumentName(row.getDocumentName());
        return agg;
    }
}
