package com.agentlego.backend.vectorstore.web;

import com.agentlego.backend.api.ApiResponse;
import com.agentlego.backend.vectorstore.application.dto.CreateVectorStoreProfileRequest;
import com.agentlego.backend.vectorstore.application.dto.UpdateVectorStoreProfileRequest;
import com.agentlego.backend.vectorstore.application.dto.VectorStoreProfileDto;
import com.agentlego.backend.vectorstore.application.service.VectorStoreProfileApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 外置向量库连接配置（Milvus/Qdrant），供知识库引用。
 */
@RestController
@RequestMapping("/vector-store-profiles")
public class VectorStoreProfileController {

    private final VectorStoreProfileApplicationService service;

    public VectorStoreProfileController(VectorStoreProfileApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<VectorStoreProfileDto>> list() {
        return ApiResponse.ok(service.listProfiles());
    }

    @GetMapping("/{id}")
    public ApiResponse<VectorStoreProfileDto> get(@PathVariable String id) {
        return ApiResponse.ok(service.getProfile(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VectorStoreProfileDto> create(@Valid @RequestBody CreateVectorStoreProfileRequest req) {
        return ApiResponse.created(service.createProfile(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<VectorStoreProfileDto> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateVectorStoreProfileRequest req
    ) {
        return ApiResponse.ok(service.updateProfile(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.deleteProfile(id);
    }
}
