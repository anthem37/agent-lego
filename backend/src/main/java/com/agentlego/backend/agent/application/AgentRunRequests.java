package com.agentlego.backend.agent.application;

import com.agentlego.backend.agent.application.dto.RunAgentRequest;

/**
 * 构造 {@link RunAgentRequest} 的工厂方法，供工作流、评测等上下文复用，避免散落重复 setter。
 */
public final class AgentRunRequests {

    private AgentRunRequests() {
    }

    public static RunAgentRequest of(String modelId, String input) {
        return of(modelId, input, null);
    }

    /**
     * @param memoryNamespace 可选；非空时与直连 {@code POST /agents/{id}/run} 的 memoryNamespace 语义一致
     */
    public static RunAgentRequest of(String modelId, String input, String memoryNamespace) {
        RunAgentRequest r = new RunAgentRequest();
        r.setModelId(modelId);
        r.setInput(input);
        if (memoryNamespace != null && !memoryNamespace.isBlank()) {
            r.setMemoryNamespace(memoryNamespace.trim());
        }
        return r;
    }
}
