package com.agentlego.backend.workflow.application.mapper;

import com.agentlego.backend.common.EnumStrings;
import com.agentlego.backend.workflow.application.dto.WorkflowDto;
import com.agentlego.backend.workflow.application.dto.WorkflowRunDto;
import com.agentlego.backend.workflow.domain.WorkflowAggregate;
import com.agentlego.backend.workflow.domain.WorkflowRunAggregate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, imports = {EnumStrings.class})
public interface WorkflowDtoMapper {

    WorkflowDto toWorkflowDto(WorkflowAggregate agg);

    @Mapping(target = "status", expression = "java(EnumStrings.nameOrNull(run.getStatus()))")
    WorkflowRunDto toRunDto(WorkflowRunAggregate run);
}
