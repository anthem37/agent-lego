package com.agentlego.backend.memory;

import com.agentlego.backend.api.ApiResponse;
import com.agentlego.backend.memory.application.MemoryApplicationService;
import com.agentlego.backend.memory.application.dto.CreateMemoryItemRequest;
import com.agentlego.backend.memory.application.dto.MemoryQueryRequest;
import com.agentlego.backend.memory.application.dto.MemoryQueryResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 记忆管理 API（Controller）。
 * <p>
 * 说明：
 * - memory item 目前以“结构化 Map + 文本 content”为主；
 * - query 当前是最小可用实现，后续可接入向量检索/多路召回。
 */
@RestController
@RequestMapping("/memory")
public class MemoryController {

    /**
     * 记忆应用服务（Application Service）。
     */
    private final MemoryApplicationService service;

    public MemoryController(MemoryApplicationService service) {
        this.service = service;
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<String> createItem(@Valid @RequestBody CreateMemoryItemRequest req) {
        String id = service.createItem(req);
        return ApiResponse.created(id);
    }

    @PostMapping("/query")
    public ApiResponse<MemoryQueryResponse> query(@Valid @RequestBody MemoryQueryRequest req) {
        return ApiResponse.ok(service.query(req));
    }
}

