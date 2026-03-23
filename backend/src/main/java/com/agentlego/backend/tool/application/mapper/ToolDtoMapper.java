package com.agentlego.backend.tool.application.mapper;

import com.agentlego.backend.tool.application.dto.ToolDto;
import com.agentlego.backend.tool.application.dto.ToolReferencesDto;
import com.agentlego.backend.tool.domain.ToolAggregate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ToolDtoMapper {

    @Mapping(
            target = "toolCategory",
            expression = "java(agg.getToolCategory() == null ? com.agentlego.backend.tool.domain.ToolCategory.ACTION.name() : agg.getToolCategory().name())"
    )
    ToolDto toDto(ToolAggregate agg);

    default ToolReferencesDto toReferencesDto(
            int referencingAgentCount,
            List<String> referencingAgentIds,
            long referencingKbDocumentCount
    ) {
        ToolReferencesDto dto = new ToolReferencesDto();
        dto.setReferencingAgentCount(referencingAgentCount);
        dto.setReferencingAgentIds(referencingAgentIds);
        dto.setReferencingKbDocumentCount(referencingKbDocumentCount);
        return dto;
    }
}
