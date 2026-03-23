package com.agentlego.backend.tool.application.support;

import com.agentlego.backend.tool.application.dto.LocalBuiltinToolMetaDto;
import com.agentlego.backend.tool.application.dto.ToolDto;
import com.agentlego.backend.tool.local.LocalBuiltinToolCatalog;
import com.agentlego.backend.tool.local.LocalToolDefinitionField;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 为 {@link com.agentlego.backend.tool.domain.ToolType#LOCAL} 的 API 响应补全 {@code definition} 中的
 * {@link LocalToolDefinitionKeys#INPUT_SCHEMA} / {@link LocalToolDefinitionKeys#OUTPUT_SCHEMA}，
 * 使历史库内空 definition 的 LOCAL 行仍可展示与 HTTP 一致的入出参表格。
 */
@Component
public class LocalToolResponseEnricher {

    private final LocalBuiltinToolCatalog localBuiltinToolCatalog;

    public LocalToolResponseEnricher(LocalBuiltinToolCatalog localBuiltinToolCatalog) {
        this.localBuiltinToolCatalog = localBuiltinToolCatalog;
    }

    private static ToolDto mergeSchemas(ToolDto dto, LocalBuiltinToolMetaDto meta) {
        Map<String, Object> def = dto.getDefinition() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(dto.getDefinition());
        if (!def.containsKey(LocalToolDefinitionField.INPUT_SCHEMA.jsonKey()) && meta.getInputSchema() != null) {
            def.put(LocalToolDefinitionField.INPUT_SCHEMA.jsonKey(), meta.getInputSchema());
        }
        if (!def.containsKey(LocalToolDefinitionField.OUTPUT_SCHEMA.jsonKey()) && meta.getOutputSchema() != null) {
            def.put(LocalToolDefinitionField.OUTPUT_SCHEMA.jsonKey(), meta.getOutputSchema());
        }
        dto.setDefinition(def);
        return dto;
    }

    public ToolDto enrichIfLocal(ToolDto dto) {
        if (dto == null || dto.getToolType() == null || !"LOCAL".equalsIgnoreCase(dto.getToolType())) {
            return dto;
        }
        return localBuiltinToolCatalog.findMetaByCanonicalName(dto.getName())
                .map(meta -> mergeSchemas(dto, meta))
                .orElse(dto);
    }
}
