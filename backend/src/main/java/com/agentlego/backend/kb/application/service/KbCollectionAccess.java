package com.agentlego.backend.kb.application.service;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.kb.domain.KbCollectionAggregate;
import com.agentlego.backend.kb.domain.KbCollectionRepository;
import com.agentlego.backend.kb.domain.KbDocumentRepository;
import com.agentlego.backend.kb.domain.KbDocumentRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 知识库应用层存在性校验：集合与「文档属于集合」约束，供 {@link KbApplicationService} 等编排复用。
 */
@Component
public class KbCollectionAccess {

    private final KbCollectionRepository collectionRepository;
    private final KbDocumentRepository documentRepository;

    public KbCollectionAccess(KbCollectionRepository collectionRepository, KbDocumentRepository documentRepository) {
        this.collectionRepository = collectionRepository;
        this.documentRepository = documentRepository;
    }

    public KbCollectionAggregate requireCollection(String collectionId) {
        return collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "知识库集合未找到", HttpStatus.NOT_FOUND));
    }

    public KbDocumentRow requireDocumentInCollection(String collectionId, String documentId) {
        KbDocumentRow doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "文档未找到", HttpStatus.NOT_FOUND));
        if (!collectionId.equals(doc.getCollectionId())) {
            throw new ApiException("VALIDATION_ERROR", "文档不属于该集合", HttpStatus.BAD_REQUEST);
        }
        return doc;
    }
}
