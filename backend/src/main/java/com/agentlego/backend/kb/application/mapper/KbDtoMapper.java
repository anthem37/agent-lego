package com.agentlego.backend.kb.application.mapper;

import com.agentlego.backend.kb.application.dto.KbCollectionDto;
import com.agentlego.backend.kb.application.dto.KbDocumentDto;
import com.agentlego.backend.kb.domain.KbCollectionAggregate;
import com.agentlego.backend.kb.domain.KbDocumentRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * KB 读模型 → API DTO；由 MapStruct 生成实现，避免手写 getter/setter。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface KbDtoMapper {

    ObjectMapper KB_JSON = new ObjectMapper();

    @Mapping(target = "chunkParams", ignore = true)
    KbCollectionDto toCollectionDto(KbCollectionAggregate aggregate);

    @AfterMapping
    default void fillChunkParams(KbCollectionAggregate src, @MappingTarget KbCollectionDto tgt) {
        String raw = src.getChunkParamsJson();
        if (raw == null || raw.isBlank()) {
            tgt.setChunkParams(Map.of());
            return;
        }
        try {
            tgt.setChunkParams(KB_JSON.readValue(raw, new TypeReference<>() {
            }));
        } catch (Exception e) {
            tgt.setChunkParams(Map.of());
        }
    }

    @Mapping(target = "linkedToolIds", ignore = true)
    @Mapping(target = "toolOutputBindings", ignore = true)
    KbDocumentDto toDocumentDto(KbDocumentRow row);

    /**
     * 列表项：不序列化大字段 body（由服务层保证不设置或忽略映射）。
     */
    @Mapping(target = "body", ignore = true)
    @Mapping(target = "bodyRich", ignore = true)
    @Mapping(target = "linkedToolIds", ignore = true)
    @Mapping(target = "toolOutputBindings", ignore = true)
    KbDocumentDto toDocumentListItemDto(KbDocumentRow row);

    @AfterMapping
    default void fillDocumentToolFields(KbDocumentRow src, @MappingTarget KbDocumentDto tgt) {
        tgt.setLinkedToolIds(parseLinkedIdListJson(src.getLinkedToolIdsJson()));
        tgt.setToolOutputBindings(parseBindingsJson(src.getToolOutputBindingsJson()));
    }

    default List<String> parseLinkedIdListJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<String> ids = KB_JSON.readValue(raw, new TypeReference<List<String>>() {
            });
            return ids == null ? List.of() : ids;
        } catch (Exception e) {
            return List.of();
        }
    }

    default Map<String, Object> parseBindingsJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of("mappings", Collections.emptyList());
        }
        try {
            Map<String, Object> m = KB_JSON.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
            return m == null ? Map.of() : m;
        } catch (Exception e) {
            return Map.of("mappings", Collections.emptyList());
        }
    }
}
