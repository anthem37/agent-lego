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
}

