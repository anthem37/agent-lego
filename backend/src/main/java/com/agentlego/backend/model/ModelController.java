package com.agentlego.backend.model;

import com.agentlego.backend.api.ApiResponse;
import com.agentlego.backend.model.application.ModelApplicationService;
import com.agentlego.backend.model.domain.ModelProvider;
import com.agentlego.backend.model.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型管理 API（Controller）。
 * <p>
 * 提供能力：
 * - 创建模型配置（provider/modelKey/apiKey/config）
 * - 列表 / 查询 / 更新 / 删除
 * - 触发一次连通性测试（最小可用能力）
 */
@RestController
@RequestMapping("/models")
public class ModelController {

    /**
     * 模型应用服务（Application Service）。
     */
    private final ModelApplicationService modelService;

    public ModelController(ModelApplicationService modelService) {
        this.modelService = modelService;
    }

    /**
     * 模型列表（轻量字段，按创建时间倒序）。
     */
    @GetMapping
    public ApiResponse<List<ModelSummaryDto>> list() {
        return ApiResponse.ok(modelService.listModels());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<String> create(@Valid @RequestBody CreateModelRequest req) {
        String id = modelService.createModel(req);
        return ApiResponse.created(id);
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
     * 返回可用的 provider 列表与配置能力提示（用于前端动态渲染）。
     * <p>
     * 注意：必须声明在 {@code GET /{id}} 之前，避免路径 {@code /providers} 被当成 id。
     */
    @GetMapping("/providers")
    public ApiResponse<List<Map<String, Object>>> providers() {
        List<Map<String, Object>> data = java.util.Arrays.stream(ModelProvider.values())
                .map(p -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("provider", p.code());
                    row.put("chatProvider", p.isChatProvider());
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

    /**
     * 模型测试：聊天模型走流式探测；Embedding 走单次 embed。
     * <p>
     * 请求体可选：{@code prompt}、{@code maxTokens}、{@code maxStreamChunks}。
     */
    @PostMapping("/{id}/test")
    public ApiResponse<TestModelResponse> test(
            @PathVariable("id") String id,
            @Valid @RequestBody(required = false) TestModelRequest body
    ) {
        return ApiResponse.ok(modelService.testModel(id, body));
    }
}

