package com.agentlego.backend.api.controller;

import com.agentlego.backend.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
/**
 * 健康检查接口（Health Check）。
 *
 * 说明：用于容器探针与简单自检，不涉及业务依赖（DB、外部模型等）。
 */
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = Map.of(
                "status", "ok",
                "timestamp", Instant.now().toString()
        );
        return ApiResponse.ok(data);
    }
}

