package com.agentlego.backend.agent.application;

import com.agentlego.backend.agent.application.dto.RunAgentRequest;

/**
 * 构造 {@link RunAgentRequest} 的工厂方法，供工作流、评测等上下文复用，避免散落重复 setter。
 */
public final class AgentRunRequests {

    private AgentRunRequests() {
    }

    public static RunAgentRequest of(String modelId, String input) {
        RunAgentRequest r = new RunAgentRequest();
        r.setModelId(modelId);
        r.setInput(input);
        return r;
    }
}
