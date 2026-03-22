package com.agentlego.backend.runtime.definition;

import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.memory.LongTermMemoryMode;
import io.agentscope.core.rag.Knowledge;

/**
 * 运行时 Agent 定义。knowledge 非空时使用通用 RAG 模式注入检索结果；
 * longTermMemory 非空时由 {@link io.agentscope.core.ReActAgent} 按 {@link LongTermMemoryMode} 挂载 Hook/工具。
 */
public record AgentDefinition(
        String name,
        String systemPrompt,
        ModelDefinition model,
        Integer maxIters,
        Knowledge knowledge,
        int knowledgeTopK,
        double knowledgeScoreThreshold,
        LongTermMemory longTermMemory,
        LongTermMemoryMode longTermMemoryMode
) {
    public AgentDefinition(String name, String systemPrompt, ModelDefinition model, Integer maxIters) {
        this(name, systemPrompt, model, maxIters, null, 3, 0.3, null, null);
    }

    public AgentDefinition(
            String name,
            String systemPrompt,
            ModelDefinition model,
            Integer maxIters,
            Knowledge knowledge,
            int knowledgeTopK,
            double knowledgeScoreThreshold
    ) {
        this(name, systemPrompt, model, maxIters, knowledge, knowledgeTopK, knowledgeScoreThreshold, null, null);
    }
}

