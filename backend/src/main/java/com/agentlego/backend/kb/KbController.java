package com.agentlego.backend.kb;

import com.agentlego.backend.api.ApiResponse;
import com.agentlego.backend.kb.application.KnowledgeBaseApplicationService;
import com.agentlego.backend.kb.application.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库 API：知识库（空间）与 知识（文档）分组路由。
 */
@RestController
@RequestMapping("/kb")
public class KbController {

    private final KnowledgeBaseApplicationService service;

    public KbController(KnowledgeBaseApplicationService service) {
        this.service = service;
    }

    // —— 知识库（元数据）——

    @GetMapping("/bases")
    public ApiResponse<List<KbBaseDto>> listBases() {
        return ApiResponse.ok(service.listBases());
    }

    @PostMapping("/bases")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KbBaseDto> createBase(@Valid @RequestBody CreateKbBaseRequest req) {
        return ApiResponse.created(service.createBase(req));
    }

    @GetMapping("/bases/{baseId}")
    public ApiResponse<KbBaseDto> getBase(@PathVariable("baseId") String baseId) {
        return ApiResponse.ok(service.getBase(baseId));
    }

    @PutMapping("/bases/{baseId}")
    public ApiResponse<KbBaseDto> updateBase(@PathVariable("baseId") String baseId,
                                             @Valid @RequestBody UpdateKbBaseRequest req) {
        return ApiResponse.ok(service.updateBase(baseId, req));
    }

    @DeleteMapping("/bases/{baseId}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> deleteBase(@PathVariable("baseId") String baseId) {
        service.deleteBase(baseId);
        return ApiResponse.ok(null);
    }

    // —— 知识（文档 / 分片）——

    @GetMapping("/bases/{baseId}/knowledge")
    public ApiResponse<KbDocumentPageDto> listKnowledge(
            @PathVariable("baseId") String baseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(service.listKnowledge(baseId, page, pageSize));
    }

    @PostMapping("/bases/{baseId}/knowledge")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KbIngestResponse> addKnowledge(@PathVariable("baseId") String baseId,
                                                      @Valid @RequestBody CreateKnowledgeRequest req) {
        return ApiResponse.created(service.ingestKnowledge(baseId, req));
    }

    /**
     * 通过绑定键添加入库（无需先查 baseId）
     */
    @PostMapping("/by-key/{kbKey}/knowledge")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KbIngestResponse> addKnowledgeByKey(@PathVariable("kbKey") String kbKey,
                                                           @Valid @RequestBody CreateKnowledgeRequest req) {
        return ApiResponse.created(service.ingestKnowledgeByKbKey(kbKey, req));
    }

    @GetMapping("/knowledge/{documentId}")
    public ApiResponse<KbKnowledgeDetailDto> getKnowledge(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(service.getKnowledge(documentId));
    }

    @DeleteMapping("/knowledge/{documentId}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> deleteKnowledge(@PathVariable("documentId") String documentId) {
        service.deleteDocument(documentId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/query")
    public ApiResponse<KbQueryResponse> query(@Valid @RequestBody KbQueryRequest req) {
        return ApiResponse.ok(service.query(req));
    }
}
