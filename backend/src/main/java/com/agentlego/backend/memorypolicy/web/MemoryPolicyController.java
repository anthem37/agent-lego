package com.agentlego.backend.memorypolicy.web;

import com.agentlego.backend.agent.application.dto.AgentRefDto;
import com.agentlego.backend.api.ApiResponse;
import com.agentlego.backend.memorypolicy.application.dto.*;
import com.agentlego.backend.memorypolicy.application.service.MemoryPolicyApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 记忆策略管理（CRUD） + 策略下条目。
 */
@RestController
@RequestMapping("/memory-policies")
public class MemoryPolicyController {

    private final MemoryPolicyApplicationService memoryPolicyApplicationService;

    public MemoryPolicyController(MemoryPolicyApplicationService memoryPolicyApplicationService) {
        this.memoryPolicyApplicationService = memoryPolicyApplicationService;
    }

    @GetMapping
    public ApiResponse<List<MemoryPolicyDto>> listPolicies() {
        return ApiResponse.ok(memoryPolicyApplicationService.listPolicies());
    }

    @GetMapping("/{id}")
    public ApiResponse<MemoryPolicyDto> getPolicy(@PathVariable("id") String id) {
        return ApiResponse.ok(memoryPolicyApplicationService.getPolicy(id));
    }

    @GetMapping("/{id}/referencing-agents")
    public ApiResponse<List<AgentRefDto>> listReferencingAgents(@PathVariable("id") String id) {
        return ApiResponse.ok(memoryPolicyApplicationService.listReferencingAgents(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<String> createPolicy(@Valid @RequestBody CreateMemoryPolicyRequest req) {
        return ApiResponse.created(memoryPolicyApplicationService.createPolicy(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> updatePolicy(@PathVariable("id") String id, @Valid @RequestBody UpdateMemoryPolicyRequest req) {
        memoryPolicyApplicationService.updatePolicy(id, req);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePolicy(@PathVariable("id") String id) {
        memoryPolicyApplicationService.deletePolicy(id);
        return ApiResponse.ok();
    }

    /**
     * 为策略下全部条目重新写入外置向量（运维/补数据）。
     */
    @PostMapping("/{id}/reindex-vectors")
    public ApiResponse<MemoryReindexVectorsResultDto> reindexVectors(@PathVariable("id") String id) {
        return ApiResponse.ok(memoryPolicyApplicationService.reindexVectors(id));
    }

    @GetMapping("/{id}/items")
    public ApiResponse<List<MemoryItemDto>> listItems(
            @PathVariable("id") String id,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "orderByTrgm", required = false) Boolean orderByTrgm
    ) {
        return ApiResponse.ok(memoryPolicyApplicationService.listItems(id, q, limit, orderByTrgm));
    }

    @PostMapping("/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<String> createItem(
            @PathVariable("id") String id,
            @Valid @RequestBody CreateMemoryItemRequest req
    ) {
        return ApiResponse.created(memoryPolicyApplicationService.createItem(id, req));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ApiResponse<Void> deleteItem(
            @PathVariable("id") String id,
            @PathVariable("itemId") String itemId
    ) {
        memoryPolicyApplicationService.deleteItem(id, itemId);
        return ApiResponse.ok();
    }
}
