package com.agentlego.backend.kb.domain;

import java.util.List;
import java.util.Optional;

public interface KbDocumentRepository {

    void insertPending(
            String id,
            String collectionId,
            String title,
            String body,
            String linkedToolIdsJson,
            String toolOutputBindingsJson
    );

    void markReady(String id);

    void markFailed(String id, String errorMessage);

    Optional<KbDocumentRow> findById(String id);

    List<KbDocumentRow> listByCollectionId(String collectionId);

    void deleteById(String id);
}
