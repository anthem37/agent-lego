package com.agentlego.backend.model.web;

import com.agentlego.backend.api.ApiResponse;
import com.agentlego.backend.model.application.dto.*;
import com.agentlego.backend.model.application.service.ModelApplicationService;
import com.agentlego.backend.model.domain.ModelProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP：模型 CRUD、provider 元数据、连通性测试。
 */
@RestController
@RequestMapping("/models")
public class ModelController {

    private final ModelApplicationService modelService;

    public ModelController(ModelApplicationService modelService) {
        this.modelService = modelService;
    }

    @GetMapping
    public ApiResponse<List<ModelSummaryDto>> list() {
        return ApiResponse.ok(modelService.listModels());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<String> create(@Valid @RequestBody CreateModelRequest req) {
        return ApiResponse.created(modelService.createModel(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable("id") String id, @Valid @RequestBody UpdateModelRequest req) {
        modelService.updateModel(id, req);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") String id) {
        modelService.deleteModel(id);
        return ApiResponse.ok();
    }

    /**
     * 必须在 `GET /{id}` 之前，避免 `providers` 被当作 id。
     */
    @GetMapping("/providers")
    public ApiResponse<List<Map<String, Object>>> providers() {
        List<Map<String, Object>> data = java.util.Arrays.stream(ModelProvider.values())
                .map(p -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("provider", p.code());
                    row.put("chatProvider", p.isChatProvider());
                    row.put("modelKind", p.isChatProvider() ? "CHAT" : "EMBEDDING");
                    row.put("supportedConfigKeys", p.supportedConfigKeys());
                    return row;
                })
                .toList();
        return ApiResponse.ok(data);
    }

    @GetMapping("/{id}")
    public ApiResponse<ModelDto> get(@PathVariable("id") String id) {
        return ApiResponse.ok(modelService.getModel(id));
    }

    @PostMapping("/{id}/test")
    public ApiResponse<TestModelResponse> test(
            @PathVariable("id") String id,
            @Valid @RequestBody(required = false) TestModelRequest body
    ) {
        return ApiResponse.ok(modelService.testModel(id, body));
    }
}
