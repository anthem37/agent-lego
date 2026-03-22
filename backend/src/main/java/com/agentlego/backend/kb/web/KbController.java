package com.agentlego.backend.kb.web;

import com.agentlego.backend.api.ApiResponse;
import com.agentlego.backend.kb.application.dto.*;
import com.agentlego.backend.kb.application.service.KbApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/kb")
public class KbController {

    private final KbApplicationService kbApplicationService;

    public KbController(KbApplicationService kbApplicationService) {
        this.kbApplicationService = kbApplicationService;
    }

    @PostMapping("/collections")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KbCollectionDto> createCollection(@Valid @RequestBody CreateKbCollectionRequest req) {
        return ApiResponse.created(kbApplicationService.createCollection(req));
    }

    @GetMapping("/meta/chunk-strategies")
    public ApiResponse<List<KbChunkStrategyMetaDto>> listChunkStrategies() {
        return ApiResponse.ok(KbChunkStrategyMetaDto.standardList());
    }

    @GetMapping("/meta/agent-policy-summaries")
    public ApiResponse<List<KbAgentPolicySummaryDto>> listAgentKbPolicySummaries() {
        return ApiResponse.ok(kbApplicationService.listAgentKbPolicySummaries());
    }

    @GetMapping("/collections")
    public ApiResponse<List<KbCollectionDto>> listCollections() {
        return ApiResponse.ok(kbApplicationService.listCollections());
    }

    @GetMapping("/collections/{id}")
    public ApiResponse<KbCollectionDto> getCollection(@PathVariable("id") String id) {
        return ApiResponse.ok(kbApplicationService.getCollection(id));
    }

    @GetMapping("/collections/{id}/documents")
    public ApiResponse<List<KbDocumentDto>> listDocuments(@PathVariable("id") String id) {
        return ApiResponse.ok(kbApplicationService.listDocuments(id));
    }

    @GetMapping("/collections/{collectionId}/documents/{documentId}")
    public ApiResponse<KbDocumentDto> getDocument(
            @PathVariable("collectionId") String collectionId,
            @PathVariable("documentId") String documentId
    ) {
        return ApiResponse.ok(kbApplicationService.getDocument(collectionId, documentId));
    }

    @PostMapping("/collections/{collectionId}/documents/{documentId}/render")
    public ApiResponse<RenderKbDocumentResponse> renderDocumentBody(
            @PathVariable("collectionId") String collectionId,
            @PathVariable("documentId") String documentId,
            @RequestBody(required = false) RenderKbDocumentRequest req
    ) {
        return ApiResponse.ok(kbApplicationService.renderDocumentBody(collectionId, documentId, req));
    }

    @PostMapping("/collections/{collectionId}/documents/{documentId}/validate")
    public ApiResponse<KbDocumentValidationResponse> validateDocument(
            @PathVariable("collectionId") String collectionId,
            @PathVariable("documentId") String documentId
    ) {
        return ApiResponse.ok(kbApplicationService.validateDocument(collectionId, documentId));
    }

    @PostMapping("/collections/{collectionId}/retrieve-preview")
    public ApiResponse<KbRetrievePreviewResponse> previewRetrieve(
            @PathVariable("collectionId") String collectionId,
            @RequestBody KbRetrievePreviewRequest req
    ) {
        return ApiResponse.ok(kbApplicationService.previewRetrieve(collectionId, req));
    }

    /**
     * 多集合联合召回调试（与智能体绑定多集合 RAG 一致）。
     */
    @PostMapping("/retrieve-preview")
    public ApiResponse<KbRetrievePreviewResponse> previewRetrieveMulti(@RequestBody KbMultiRetrievePreviewRequest req) {
        return ApiResponse.ok(kbApplicationService.previewRetrieveMulti(req));
    }

    /**
     * 批量校验某集合下全部文档。
     */
    @PostMapping("/collections/{collectionId}/documents/validate-all")
    public ApiResponse<KbCollectionDocumentsValidationResponse> validateAllDocumentsInCollection(
            @PathVariable("collectionId") String collectionId,
            @RequestBody(required = false) KbValidateCollectionDocumentsRequest req
    ) {
        return ApiResponse.ok(kbApplicationService.validateCollectionDocuments(collectionId, req));
    }

    @PostMapping("/collections/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KbDocumentDto> ingestDocument(
            @PathVariable("id") String id,
            @Valid @RequestBody IngestKbDocumentRequest req
    ) {
        return ApiResponse.created(kbApplicationService.ingestTextDocument(id, req));
    }

    @PutMapping("/collections/{collectionId}/documents/{documentId}")
    public ApiResponse<KbDocumentDto> updateDocument(
            @PathVariable("collectionId") String collectionId,
            @PathVariable("documentId") String documentId,
            @Valid @RequestBody IngestKbDocumentRequest req
    ) {
        return ApiResponse.ok(kbApplicationService.updateTextDocument(collectionId, documentId, req));
    }

    @DeleteMapping("/collections/{collectionId}/documents/{documentId}")
    public ApiResponse<Void> deleteDocument(
            @PathVariable("collectionId") String collectionId,
            @PathVariable("documentId") String documentId
    ) {
        kbApplicationService.deleteDocument(collectionId, documentId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/collections/{id}")
    public ApiResponse<KbCollectionDeleteResult> deleteCollection(@PathVariable("id") String id) {
        return ApiResponse.ok(kbApplicationService.deleteCollection(id));
    }
}
