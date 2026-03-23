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

    /**
     * 可选：记忆条目命名空间（写入 metadata.memoryNamespace，检索/去重时按策略 id + 该字段隔离）。
     * 不传或空字符串表示「全局」：与历史未带该字段的条目共用同一池。
     */
    private String memoryNamespace;

    @NotBlank
    private String input;
}
