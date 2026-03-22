package com.agentlego.backend.agent.application.dto;

import lombok.Data;

/**
 * 试运行时记忆侧可观测信息（与模型最终输出独立，便于联调闭环）。
 */
@Data
public class AgentRunMemoryDebug {
    private String memoryPolicyId;
    private String memoryPolicyName;
    private String ownerScope;
    private String retrievalMode;
    private String writeMode;
    private Integer previewHitCount;
    /**
     * 与当前用户输入、策略 topK 一致的关键词检索预览（截断）。
     */
    private String previewText;
}
