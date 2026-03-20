package com.agentlego.backend.agent.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建智能体请求 DTO。
 * <p>
 * - systemPrompt：作为 agent 的 system message（可被 memory/KB 策略注入额外上下文）
 * - toolIds：允许使用的工具列表（工具权限）
 * - memoryPolicy / knowledgeBasePolicy：检索与注入策略（当前以 JSON object 形式承载）
 */
@Data
public class CreateAgentRequest {

    /**
     * 智能体名称（name）。
     */
    @NotBlank
    private String name;

    /**
     * 系统提示词（system prompt）。
     */
    @NotBlank
    private String systemPrompt;

    /**
     * 绑定的默认模型 ID（modelId）。
     */
    @NotBlank
    private String modelId;

    /**
     * 允许使用的工具 ID 列表（tool permissions）。
     */
    private List<String> toolIds;

    /**
     * 记忆策略（memory policy），例如 topK、queryTemplate 等。
     */
    private Map<String, Object> memoryPolicy;

    /**
     * 知识库策略（knowledge base policy），例如 kbId、topK、queryTemplate 等。
     */
    private Map<String, Object> knowledgeBasePolicy;
}

