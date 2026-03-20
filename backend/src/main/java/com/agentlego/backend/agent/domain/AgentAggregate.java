package com.agentlego.backend.agent.domain;

import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 智能体聚合根（Aggregate Root）。
 * <p>
 * 说明：
 * - 该对象代表平台侧“智能体”的核心配置快照；
 * - memoryPolicy / knowledgeBasePolicy 以 JSON object（Map）承载，便于策略迭代与灰度。
 */
@Data
public class AgentAggregate {
    /**
     * 智能体 ID（Snowflake 字符串）。
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
     * 允许使用的工具 ID 列表（tool permissions）。
     */
    private List<String> toolIds;
    /**
     * 记忆检索/注入策略（JSON object）。
     */
    private Map<String, Object> memoryPolicy;
    /**
     * 知识库检索/注入策略（JSON object）。
     */
    private Map<String, Object> knowledgeBasePolicy;
    /**
     * 创建时间。
     */
    private Instant createdAt;
}

