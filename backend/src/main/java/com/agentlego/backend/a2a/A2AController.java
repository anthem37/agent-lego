package com.agentlego.backend.a2a;

import com.agentlego.backend.a2a.dto.A2ADelegateRequest;
import com.agentlego.backend.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * A2A 网关 API（Controller）。
 * <p>
 * 说明：
 * - 当前仅实现最小“本地委派”能力：把请求转交给本地 agent 执行；
 * - 后续可扩展为对接 Google/community A2A 标准（capability discovery、streaming 等）。
 */
@RestController
@RequestMapping("/a2a")
public class A2AController {

    /**
     * A2A 网关服务（当前为本地委派实现）。
     */
    private final A2AGatewayService service;

    public A2AController(A2AGatewayService service) {
        this.service = service;
    }

    @PostMapping("/delegate")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<String> delegate(@Valid @RequestBody A2ADelegateRequest req) {
        String out = service.delegateLocal(req.getAgentId(), req.getModelId(), req.getInput());
        return ApiResponse.ok(out);
    }
}

