package com.agentlego.backend.agent.application.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 智能体 DTO。
 * <p>
 * 说明：用于 API 输出智能体配置快照。
 */
@Data
public class AgentDto {
    /**
     * 智能体 ID。
     */
    private String id;
    /**
     * 智能体名称。
     */
    private String name;
    /**
     * 系统提示词（system prompt）。
     */
    private String systemPrompt;
    /**
     * 绑定的默认模型 ID。
     */
    private String modelId;
    /**
     * 允许使用的工具 ID 列表。
     */
    private List<String> toolIds;
    /**
     * 记忆策略（JSON object）。
     */
    private Map<String, Object> memoryPolicy;
    /**
     * 知识库策略（JSON object）。
     */
    private Map<String, Object> knowledgeBasePolicy;
    /**
     * 创建时间。
     */
    private Instant createdAt;
}

