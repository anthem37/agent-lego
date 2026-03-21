package com.agentlego.backend.kb.infrastructure;

import com.agentlego.backend.kb.domain.KbChunkHit;
import com.agentlego.backend.kb.domain.KbChunkRepository;
import com.agentlego.backend.kb.infrastructure.persistence.KbChunkDO;
import com.agentlego.backend.kb.infrastructure.persistence.KbChunkMapper;
import com.agentlego.backend.kb.support.KbPgVectorLiteral;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class KbChunkRepositoryImpl implements KbChunkRepository {

    private static final int MAX_FULLTEXT_QUERY_CHARS = 2048;

    private final KbChunkMapper mapper;

    public KbChunkRepositoryImpl(KbChunkMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void deleteByDocumentId(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return;
        }
        mapper.deleteByDocumentId(documentId);
    }

    @Override
    public void insert(
            String id,
            String documentId,
            String collectionId,
            int chunkIndex,
            String content,
            String embeddingText,
            String metadataJson,
            float[] embeddingForStorage
    ) {
        String lit = KbPgVectorLiteral.format(embeddingForStorage);
        String meta = metadataJson == null || metadataJson.isBlank() ? "{}" : metadataJson;
        mapper.insert(id, documentId, collectionId, chunkIndex, content, embeddingText, meta, lit);
    }

    @Override
    public List<KbChunkHit> searchByCosineSimilarity(
            List<String> collectionIds,
            float[] queryEmbeddingPadded,
            int limit
    ) {
        if (collectionIds == null || collectionIds.isEmpty() || limit <= 0) {
            return List.of();
        }
        String q = KbPgVectorLiteral.format(queryEmbeddingPadded);
        List<KbChunkDO> rows = mapper.searchByCosineSimilarity(collectionIds, q, limit);
        if (rows == null) {
            return List.of();
        }
        return rows.stream().map(this::toSearchHit).toList();
    }

    @Override
    public List<KbChunkHit> searchByFullText(List<String> collectionIds, String query, int limit) {
        if (collectionIds == null || collectionIds.isEmpty() || limit <= 0) {
            return List.of();
        }
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        if (q.length() > MAX_FULLTEXT_QUERY_CHARS) {
            q = q.substring(0, MAX_FULLTEXT_QUERY_CHARS);
        }
        List<KbChunkDO> rows = mapper.searchByFullText(collectionIds, q, limit);
        if (rows == null) {
            return List.of();
        }
        return rows.stream().map(this::toSearchHit).toList();
    }

    private KbChunkHit toSearchHit(KbChunkDO row) {
        KbChunkHit h = new KbChunkHit();
        h.setId(row.getId());
        h.setDocumentId(row.getDocumentId());
        h.setContent(row.getContent());
        h.setSimilarity(row.getSimilarity());
        return h;
    }
}
