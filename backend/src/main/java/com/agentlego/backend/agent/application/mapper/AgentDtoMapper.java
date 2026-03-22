package com.agentlego.backend.agent.application.mapper;

import com.agentlego.backend.agent.application.dto.AgentDto;
import com.agentlego.backend.agent.domain.AgentAggregate;
import com.agentlego.backend.model.domain.ModelAggregate;
import com.agentlego.backend.model.support.ModelConfigSummaries;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.Optional;

/**
 * 智能体聚合 → DTO；绑定模型的展示字段通过 {@link #toDto(AgentAggregate, Optional)} 合并。
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        imports = {ModelConfigSummaries.class}
)
public interface AgentDtoMapper {

    @Mapping(target = "modelDisplayName", ignore = true)
    @Mapping(target = "modelProvider", ignore = true)
    @Mapping(target = "modelModelKey", ignore = true)
    @Mapping(target = "modelConfigSummary", ignore = true)
    @Mapping(target = "memoryPolicyName", ignore = true)
    @Mapping(target = "memoryPolicyOwnerScope", ignore = true)
    AgentDto fromAggregate(AgentAggregate agg);

    default AgentDto toDto(AgentAggregate agg, Optional<ModelAggregate> boundModel) {
        if (agg == null) {
            return null;
        }
        AgentDto dto = fromAggregate(agg);
        boundModel.ifPresent(m -> {
            dto.setModelDisplayName(m.getName());
            dto.setModelProvider(m.getProvider());
            dto.setModelModelKey(m.getModelKey());
            dto.setModelConfigSummary(ModelConfigSummaries.summarize(m.getConfig()));
        });
        return dto;
    }
}
