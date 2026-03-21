package com.agentlego.backend.a2a.web;

import com.agentlego.backend.a2a.dto.A2ADelegateRequest;
import com.agentlego.backend.a2a.service.A2AGatewayService;
import com.agentlego.backend.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP：A2A 本地委派入口。
 */
@RestController
@RequestMapping("/a2a")
public class A2AController {

    private final A2AGatewayService service;

    public A2AController(A2AGatewayService service) {
        this.service = service;
    }

    @PostMapping("/delegate")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<String> delegate(@Valid @RequestBody A2ADelegateRequest req) {
        return ApiResponse.ok(service.delegateLocal(req.getAgentId(), req.getModelId(), req.getInput()));
    }
}
