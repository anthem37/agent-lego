package com.agentlego.backend.memory.application.mapper;

import com.agentlego.backend.memory.application.dto.MemoryItemDto;
import com.agentlego.backend.memory.domain.MemoryItemAggregate;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MemoryDtoMapper {

    MemoryItemDto toDto(MemoryItemAggregate agg);
}
