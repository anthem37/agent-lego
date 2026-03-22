package com.agentlego.backend.agent.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 运行智能体请求 DTO。
 */
@Data
public class RunAgentRequest {

    private String modelId;

    private Map<String, Object> options;

    @NotBlank
    private String input;
}
