package com.agentlego.backend.model.application.mapper;

import com.agentlego.backend.model.application.dto.ModelDto;
import com.agentlego.backend.model.application.dto.ModelSummaryDto;
import com.agentlego.backend.model.domain.ModelAggregate;
import com.agentlego.backend.model.domain.ModelProvider;
import com.agentlego.backend.model.support.ModelConfigSummaries;
import org.mapstruct.*;

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
    @Mapping(target = "chatProvider", ignore = true)
    ModelSummaryDto toSummaryDto(ModelAggregate agg);

    @AfterMapping
    default void fillChatProviderFlag(ModelAggregate source, @MappingTarget ModelSummaryDto target) {
        if (source == null || source.getProvider() == null || source.getProvider().isBlank()) {
            return;
        }
        try {
            target.setChatProvider(ModelProvider.from(source.getProvider()).isChatProvider());
        } catch (IllegalArgumentException ignored) {
            target.setChatProvider(null);
        }
    }
}
