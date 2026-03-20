package com.agentlego.backend.kb.domain;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseRepository {

    /* ---------- 知识库（kb_bases） ---------- */

    String insertBase(String kbKey, String name, String description);

    int updateBase(String id, String name, String description);

    int deleteBase(String id);

    Optional<KbBaseSummary> findBaseById(String id);

    Optional<KbBaseSummary> findBaseByKbKey(String kbKey);

    List<KbBaseSummary> listBasesWithStats();

    /* ---------- 知识文档（kb_documents） ---------- */

    String createDocument(String baseId, String name, String contentRich, String contentFormat, String chunkStrategy);

    int deleteDocument(String documentId);

    List<KbDocumentSummary> listDocumentsByBaseId(String baseId, long offset, int limit);

    long countDocumentsByBaseId(String baseId);

    Optional<KbDocumentDetail> findDocumentById(String documentId);

    void saveChunks(String documentId, List<KbChunkAggregate> chunks);

    List<KbChunkAggregate> queryChunksByBaseId(String baseId, String queryText, int topK, String embeddingModelId);
}
