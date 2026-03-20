package com.agentlego.backend.kb.infrastructure;

import com.agentlego.backend.common.JacksonHolder;
import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.kb.chunk.KbChunkStrategies;
import com.agentlego.backend.kb.domain.*;
import com.agentlego.backend.kb.infrastructure.persistence.*;
import com.agentlego.backend.model.support.ModelEmbeddingClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;

@Repository
public class KnowledgeBaseRepositoryImpl implements KnowledgeBaseRepository {

    private final KbBaseMapper baseMapper;
    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final ModelEmbeddingClient embeddingClient;

    public KnowledgeBaseRepositoryImpl(
            KbBaseMapper baseMapper,
            KbDocumentMapper documentMapper,
            KbChunkMapper chunkMapper,
            ModelEmbeddingClient embeddingClient
    ) {
        this.baseMapper = baseMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.embeddingClient = embeddingClient;
    }

    private static String getEmbeddingModelIdFromMetadata(String metadataJson) {
        Map<String, Object> meta = JsonMaps.parseObject(metadataJson);
        Object v = meta.get("embedding_model_id");
        if (v == null) {
            return "";
        }
        String s = String.valueOf(v);
        return s.isBlank() ? "" : s.trim();
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null) {
            return Double.NaN;
        }
        if (a.length != b.length) {
            return Double.NaN;
        }
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (int i = 0; i < a.length; i++) {
            double x = a[i];
            double y = b[i];
            dot += x * y;
            na += x * x;
            nb += y * y;
        }
        if (na == 0.0 || nb == 0.0) {
            return Double.NaN;
        }
        return dot / Math.sqrt(na * nb);
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
    public List<KbChunkAggregate> queryChunksByBaseId(String baseId, String queryText, int topK, String embeddingModelId) {
        if (embeddingModelId == null || embeddingModelId.isBlank()) {
            List<KbChunkDO> rows = chunkMapper.searchChunks(baseId, queryText, topK);
            if (rows == null) {
                return Collections.emptyList();
            }
            return rows.stream().map(this::toAggregate).toList();
        }
        return vectorQuery(baseId, queryText, topK, embeddingModelId.trim());
    }

    private List<KbChunkAggregate> vectorQuery(String baseId, String queryText, int topK, String embeddingModelId) {
        List<KbChunkDO> chunks = chunkMapper.listChunksByBaseIdWithEmbedding(baseId);
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }

        List<float[]> queryVecs = embeddingClient.embed(embeddingModelId, List.of(queryText));
        if (queryVecs.isEmpty() || queryVecs.get(0) == null) {
            return Collections.emptyList();
        }
        float[] queryVec = queryVecs.get(0);

        // 懒加载：仅对缺失/不匹配的 chunk 补齐 embedding
        List<KbChunkDO> needEmbed = new ArrayList<>();
        List<String> needEmbedTexts = new ArrayList<>();
        for (KbChunkDO c : chunks) {
            String metaModelId = getEmbeddingModelIdFromMetadata(c.getMetadataJson());
            boolean missing = c.getEmbeddingJson() == null || c.getEmbeddingJson().isBlank();
            boolean mismatch = metaModelId == null || metaModelId.isBlank() || !embeddingModelId.equals(metaModelId);
            if (missing || mismatch) {
                needEmbed.add(c);
                needEmbedTexts.add(c.getContent());
            }
        }

        if (!needEmbed.isEmpty()) {
            List<float[]> vecs = embeddingClient.embed(embeddingModelId, needEmbedTexts);
            if (vecs.size() != needEmbed.size()) {
                throw new com.agentlego.backend.api.ApiException(
                        "UPSTREAM_ERROR",
                        "embedding batch size mismatch",
                        HttpStatus.BAD_GATEWAY
                );
            }
            for (int i = 0; i < needEmbed.size(); i++) {
                KbChunkDO c = needEmbed.get(i);
                float[] vec = vecs.get(i);
                String embeddingJson;
                try {
                    embeddingJson = JacksonHolder.INSTANCE.writeValueAsString(vec);
                } catch (Exception e) {
                    throw new com.agentlego.backend.api.ApiException(
                            "SERIALIZE_ERROR",
                            "failed to serialize embedding: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR
                    );
                }

                Map<String, Object> meta = JsonMaps.parseObject(c.getMetadataJson());
                meta.put("embedding_model_id", embeddingModelId);
                String metadataJson = JsonMaps.toJson(meta);

                chunkMapper.updateChunkEmbedding(c.getId(), embeddingJson, metadataJson);
                c.setEmbeddingJson(embeddingJson);
                c.setMetadataJson(metadataJson);
            }
        }

        PriorityQueue<ChunkScore> heap = new PriorityQueue<>(Comparator.comparingDouble(a -> a.score));
        for (KbChunkDO c : chunks) {
            if (c.getEmbeddingJson() == null || c.getEmbeddingJson().isBlank()) {
                continue;
            }
            float[] vec = parseEmbedding(c.getEmbeddingJson());
            if (vec == null) {
                continue;
            }
            double score = cosineSimilarity(queryVec, vec);
            if (Double.isNaN(score)) {
                continue;
            }
            if (heap.size() < topK) {
                heap.add(new ChunkScore(c, score));
            } else if (score > heap.peek().score) {
                heap.poll();
                heap.add(new ChunkScore(c, score));
            }
        }

        List<ChunkScore> ranked = new ArrayList<>(heap);
        ranked.sort((a, b) -> Double.compare(b.score, a.score));

        List<KbChunkAggregate> out = new ArrayList<>(ranked.size());
        for (ChunkScore cs : ranked) {
            out.add(toAggregate(cs.chunk));
        }
        return out;
    }

    private float[] parseEmbedding(String embeddingJson) {
        try {
            return JacksonHolder.INSTANCE.readValue(embeddingJson, float[].class);
        } catch (Exception e) {
            return null;
        }
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

    private static final class ChunkScore {
        private final KbChunkDO chunk;
        private final double score;

        private ChunkScore(KbChunkDO chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }
    }
}
