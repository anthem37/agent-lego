package com.agentlego.backend.model;

import com.agentlego.backend.api.ApiResponse;
import com.agentlego.backend.model.application.ModelApplicationService;
import com.agentlego.backend.model.domain.ModelProvider;
import com.agentlego.backend.model.dto.CreateModelRequest;
import com.agentlego.backend.model.dto.ModelDto;
import com.agentlego.backend.model.dto.TestModelResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 模型管理 API（Controller）。
 * <p>
 * 提供能力：
 * - 创建模型配置（provider/modelKey/apiKey/config）
 * - 查询模型详情
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<String> create(@Valid @RequestBody CreateModelRequest req) {
        String id = modelService.createModel(req);
        return ApiResponse.created(id);
    }

    @GetMapping("/{id}")
    public ApiResponse<ModelDto> get(@PathVariable("id") String id) {
        return ApiResponse.ok(modelService.getModel(id));
    }

    @PostMapping("/{id}/test")
    public ApiResponse<TestModelResponse> test(@PathVariable("id") String id) {
        return ApiResponse.ok(modelService.testModel(id));
    }

    /**
     * 返回可用的 provider 列表与配置能力提示（用于前端动态渲染）。
     */
    @GetMapping("/providers")
    public ApiResponse<List<Map<String, Object>>> providers() {
        List<Map<String, Object>> data = java.util.Arrays.stream(ModelProvider.values())
                .map(p -> Map.<String, Object>of(
                        "provider", p.code(),
                        "supportedConfigKeys", p.supportedConfigKeys()
                ))
                .toList();
        return ApiResponse.ok(data);
    }
}

