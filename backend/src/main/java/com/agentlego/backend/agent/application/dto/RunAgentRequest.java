package com.agentlego.backend.agent.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 运行智能体请求 DTO。
 * <p>
 * 说明：可指定 modelId 覆盖智能体默认模型，若不传则使用智能体绑定模型。
 */
@Data
public class RunAgentRequest {

    /**
     * 模型 ID（引用平台内模型配置）。
     */
    private String modelId;

    /**
     * 运行期覆盖参数（options）。
     * <p>
     * 说明：
     * - 会与模型配置中的 config 合并（options 优先）；
     * - 用于一次性覆盖 temperature/maxTokens/headers 等参数。
     */
    private Map<String, Object> options;

    /**
     * 用户输入（input prompt）。
     */
    @NotBlank
    private String input;
}

