package com.agentlego.backend.kb.domain;

import java.util.List;

public interface KnowledgeBaseRepository {
    String ensureDocument(String kbKey, String name);

    void saveChunks(String documentId, List<KbChunkAggregate> chunks);

    List<KbChunkAggregate> queryChunks(String kbKey, String queryText, int topK);
}

