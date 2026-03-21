package com.agentlego.backend.kb.application.mapper;

import com.agentlego.backend.kb.application.dto.KbCollectionDto;
import com.agentlego.backend.kb.application.dto.KbDocumentDto;
import com.agentlego.backend.kb.domain.KbCollectionAggregate;
import com.agentlego.backend.kb.domain.KbDocumentRow;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * KB 读模型 → API DTO；由 MapStruct 生成实现，避免手写 getter/setter。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface KbDtoMapper {

    KbCollectionDto toCollectionDto(KbCollectionAggregate aggregate);

    KbDocumentDto toDocumentDto(KbDocumentRow row);
}
