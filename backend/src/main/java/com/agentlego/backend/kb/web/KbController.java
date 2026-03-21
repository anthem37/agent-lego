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
    public ApiResponse<String> createCollection(@Valid @RequestBody CreateKbCollectionRequest req) {
        return ApiResponse.created(kbApplicationService.createCollection(req));
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

    @PostMapping("/collections/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<String> ingestDocument(
            @PathVariable("id") String id,
            @Valid @RequestBody IngestKbDocumentRequest req
    ) {
        return ApiResponse.created(kbApplicationService.ingestTextDocument(id, req));
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
