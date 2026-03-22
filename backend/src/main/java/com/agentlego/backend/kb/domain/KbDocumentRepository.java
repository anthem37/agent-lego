package com.agentlego.backend.kb.domain;

import java.util.List;
import java.util.Optional;

public interface KbDocumentRepository {

    void insertPending(
            String id,
            String collectionId,
            String title,
            String body,
            String bodyRich,
            String linkedToolIdsJson,
            String toolOutputBindingsJson,
            String similarQueriesJson
    );

    void markReady(String id);

    void markFailed(String id, String errorMessage);

    /**
     * 更新正文与绑定并置为 PENDING，供重新分片与向量化（调用方应先删除外置向量库中该文档的旧向量）。
     */
    void updateReingest(
            String id,
            String title,
            String body,
            String bodyRich,
            String linkedToolIdsJson,
            String toolOutputBindingsJson,
            String similarQueriesJson
    );

    Optional<KbDocumentRow> findById(String id);

    /**
     * 按 id 批量加载（RAG 按 document_id enrich 片段）；id 去重、忽略空串。
     */
    List<KbDocumentRow> findByIds(List<String> ids);

    List<KbDocumentRow> listByCollectionId(String collectionId);

    void deleteById(String id);
}
