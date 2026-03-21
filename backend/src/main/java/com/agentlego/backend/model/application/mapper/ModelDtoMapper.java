package com.agentlego.backend.model.application.mapper;

import com.agentlego.backend.model.application.dto.ModelDto;
import com.agentlego.backend.model.application.dto.ModelSummaryDto;
import com.agentlego.backend.model.domain.ModelAggregate;
import com.agentlego.backend.model.support.ModelConfigSummaries;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        unmappedSourcePolicy = ReportingPolicy.IGNORE,
        imports = {ModelConfigSummaries.class}
)
public interface ModelDtoMapper {

    @Mapping(
            target = "apiKeyConfigured",
            expression = "java(agg.getApiKeyCipher() != null && !agg.getApiKeyCipher().isBlank())"
    )
    ModelDto toDto(ModelAggregate agg);

    @Mapping(target = "configSummary", expression = "java(ModelConfigSummaries.summarize(agg.getConfig()))")
    ModelSummaryDto toSummaryDto(ModelAggregate agg);
}
