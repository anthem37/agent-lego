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
 * - knowledgeBasePolicy 以 JSON object（Map）承载，便于策略迭代与灰度。
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
     * 引用的记忆策略 ID（{@code lego_memory_policies.id}）；为空表示不启用平台长期记忆检索。
     */
    private String memoryPolicyId;
    /**
     * 知识库 RAG 策略（JSON object），例如 collectionIds、topK、scoreThreshold。
     */
    private Map<String, Object> knowledgeBasePolicy;
    /**
     * AgentScope 运行时形态：REACT = ReActAgent（工具 + 多步推理）；
     * CHAT = 轻量对话（不挂载工具，运行时 maxIters=1）。
     */
    private String runtimeKind;
    /**
     * ReAct 最大迭代步数（对应 ReActAgent.Builder.maxIters），仅 {@code REACT} 时有效；默认 10。
     */
    private Integer maxReactIters;
    /**
     * 创建时间。
     */
    private Instant createdAt;
}

