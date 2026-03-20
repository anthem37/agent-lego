package com.agentlego.backend.runtime.definition;

import io.agentscope.core.rag.Knowledge;

/**
 * 运行时 Agent 定义。knowledge 非空时使用 AgentScope RAG 模式注入检索结果。
 */
public record AgentDefinition(
        String name,
        String systemPrompt,
        ModelDefinition model,
        Integer maxIters,
        Knowledge knowledge,
        int knowledgeTopK,
        double knowledgeScoreThreshold
) {
    public AgentDefinition(String name, String systemPrompt, ModelDefinition model, Integer maxIters) {
        this(name, systemPrompt, model, maxIters, null, 3, 0.3);
    }
}

