package com.agentlego.backend.agent.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建 / 更新智能体共用的请求体（{@code POST /agents} 与 {@code PUT /agents/{id}} 契约一致）。
 */
@Data
public class CreateAgentRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String systemPrompt;

    @NotBlank
    private String modelId;

    private List<String> toolIds;

    private Map<String, Object> knowledgeBasePolicy;

    /**
     * 记忆策略 ID（可选）；绑定后运行时按策略检索/写回。
     */
    private String memoryPolicyId;

    /**
     * 运行时形态：{@code REACT}（默认）= ReActAgent；{@code CHAT} = 轻量对话。
     */
    private String runtimeKind;

    /**
     * ReAct 最大迭代，仅 REACT 时有效；默认 10。
     */
    private Integer maxReactIters;
}
