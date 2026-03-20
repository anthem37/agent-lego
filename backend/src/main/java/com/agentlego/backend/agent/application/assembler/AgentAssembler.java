package com.agentlego.backend.agent.application.assembler;

import com.agentlego.backend.agent.application.dto.AgentDto;
import com.agentlego.backend.agent.domain.AgentAggregate;
import com.agentlego.backend.model.domain.ModelAggregate;
import com.agentlego.backend.model.support.ModelConfigSummaries;

import java.util.Optional;

/**
 * 智能体聚合 → API DTO；模型展示字段由可选的 {@link ModelAggregate} 填充。
 */
public final class AgentAssembler {

    private AgentAssembler() {
    }

    public static AgentDto toDto(AgentAggregate agg, Optional<ModelAggregate> boundModel) {
        if (agg == null) {
            return null;
        }
        AgentDto dto = new AgentDto();
        dto.setId(agg.getId());
        dto.setName(agg.getName());
        dto.setSystemPrompt(agg.getSystemPrompt());
        dto.setModelId(agg.getModelId());
        dto.setToolIds(agg.getToolIds());
        dto.setMemoryPolicy(agg.getMemoryPolicy());
        dto.setKnowledgeBasePolicy(agg.getKnowledgeBasePolicy());
        dto.setCreatedAt(agg.getCreatedAt());
        boundModel.ifPresent(m -> {
            dto.setModelDisplayName(m.getName());
            dto.setModelProvider(m.getProvider());
            dto.setModelModelKey(m.getModelKey());
            dto.setModelConfigSummary(ModelConfigSummaries.summarize(m.getConfig()));
        });
        return dto;
    }
}
