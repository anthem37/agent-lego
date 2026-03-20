package com.agentlego.backend.tool.application.assembler;

import com.agentlego.backend.tool.application.dto.ToolDto;
import com.agentlego.backend.tool.application.dto.ToolReferencesDto;
import com.agentlego.backend.tool.domain.ToolAggregate;

import java.util.List;

/**
 * 工具聚合 → API DTO（Application 层）。
 */
public final class ToolAssembler {

    private ToolAssembler() {
    }

    public static ToolDto toDto(ToolAggregate agg) {
        if (agg == null) {
            return null;
        }
        ToolDto dto = new ToolDto();
        dto.setId(agg.getId());
        dto.setToolType(agg.getToolType().name());
        dto.setName(agg.getName());
        dto.setDefinition(agg.getDefinition());
        dto.setCreatedAt(agg.getCreatedAt());
        return dto;
    }

    public static ToolReferencesDto toReferencesDto(int referencingAgentCount, List<String> referencingAgentIds) {
        ToolReferencesDto dto = new ToolReferencesDto();
        dto.setReferencingAgentCount(referencingAgentCount);
        dto.setReferencingAgentIds(referencingAgentIds);
        return dto;
    }
}
