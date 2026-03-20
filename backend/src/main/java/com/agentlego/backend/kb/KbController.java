package com.agentlego.backend.kb;

import com.agentlego.backend.api.ApiResponse;
import com.agentlego.backend.kb.application.KnowledgeBaseApplicationService;
import com.agentlego.backend.kb.application.dto.CreateKbDocumentRequest;
import com.agentlego.backend.kb.application.dto.KbIngestResponse;
import com.agentlego.backend.kb.application.dto.KbQueryRequest;
import com.agentlego.backend.kb.application.dto.KbQueryResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 知识库 API（Controller）。
 * <p>
 * 提供能力：
 * - ingest：写入文档并自动分片（chunking）
 * - query：按 queryText 检索相关 chunk，返回用于 RAG 的上下文
 */
@RestController
@RequestMapping("/kb")
public class KbController {

    /**
     * 知识库应用服务（Application Service）。
     */
    private final KnowledgeBaseApplicationService service;

    public KbController(KnowledgeBaseApplicationService service) {
        this.service = service;
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KbIngestResponse> ingest(@Valid @RequestBody CreateKbDocumentRequest req) {
        return ApiResponse.created(service.ingest(req));
    }

    @PostMapping("/{kbKey}/ingest")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KbIngestResponse> ingestAlias(@PathVariable("kbKey") String kbKey,
                                                     @Valid @RequestBody CreateKbDocumentRequest req) {
        req.setKbKey(kbKey);
        return ApiResponse.created(service.ingest(req));
    }

    @PostMapping("/query")
    public ApiResponse<KbQueryResponse> query(@Valid @RequestBody KbQueryRequest req) {
        return ApiResponse.ok(service.query(req));
    }
}
