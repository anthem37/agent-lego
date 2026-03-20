package com.agentlego.backend.runtime.definition;

public record AgentDefinition(
        String name,
        String systemPrompt,
        ModelDefinition model,
        Integer maxIters
) {
}

