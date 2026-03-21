package com.agentlego.backend.memory.web;

import com.agentlego.backend.api.ApiResponse;
import com.agentlego.backend.memory.application.dto.CreateMemoryItemRequest;
import com.agentlego.backend.memory.application.dto.MemoryQueryRequest;
import com.agentlego.backend.memory.application.dto.MemoryQueryResponse;
import com.agentlego.backend.memory.application.service.MemoryApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP：记忆写入与查询。
 */
@RestController
@RequestMapping("/memory")
public class MemoryController {

    private final MemoryApplicationService service;

    public MemoryController(MemoryApplicationService service) {
        this.service = service;
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<String> createItem(@Valid @RequestBody CreateMemoryItemRequest req) {
        return ApiResponse.created(service.createItem(req));
    }

    @PostMapping("/query")
    public ApiResponse<MemoryQueryResponse> query(@Valid @RequestBody MemoryQueryRequest req) {
        return ApiResponse.ok(service.query(req));
    }
}
