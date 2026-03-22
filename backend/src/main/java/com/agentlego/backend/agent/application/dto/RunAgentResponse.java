package com.agentlego.backend.agent.application.dto;

import lombok.Data;

/**
 * 运行智能体响应 DTO。
 */
@Data
public class RunAgentResponse {
    /**
     * 智能体输出（final answer）。
     */
    private String output;
    /**
     * 若绑定了记忆策略，返回检索预览等调试信息（可选）。
     */
    private AgentRunMemoryDebug memory;
}
