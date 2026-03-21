package com.agentlego.backend.eval.application.mapper;

import com.agentlego.backend.common.EnumStrings;
import com.agentlego.backend.eval.application.dto.RunEvaluationDto;
import com.agentlego.backend.eval.domain.EvaluationRunAggregate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, imports = {EnumStrings.class})
public interface EvaluationRunDtoMapper {

    @Mapping(target = "status", expression = "java(EnumStrings.nameOrNull(run.getStatus()))")
    RunEvaluationDto toRunDto(EvaluationRunAggregate run);
}
