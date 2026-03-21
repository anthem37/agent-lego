package com.agentlego.backend.tool.application.mapper;

import com.agentlego.backend.tool.application.dto.ToolDto;
import com.agentlego.backend.tool.application.dto.ToolReferencesDto;
import com.agentlego.backend.tool.domain.ToolAggregate;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ToolDtoMapper {

    ToolDto toDto(ToolAggregate agg);

    default ToolReferencesDto toReferencesDto(int referencingAgentCount, List<String> referencingAgentIds) {
        ToolReferencesDto dto = new ToolReferencesDto();
        dto.setReferencingAgentCount(referencingAgentCount);
        dto.setReferencingAgentIds(referencingAgentIds);
        return dto;
    }
}
