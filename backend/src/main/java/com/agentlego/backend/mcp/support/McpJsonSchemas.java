package com.agentlego.backend.mcp.support;

import com.agentlego.backend.tool.application.dto.LocalBuiltinParamMetaDto;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.*;

/**
 * 将平台 LOCAL 内置参数元数据转为 MCP {@code inputSchema}（JSON Schema 子集）。
 */
public final class McpJsonSchemas {

    private McpJsonSchemas() {
    }

    public static McpSchema.JsonSchema fromBuiltinParams(List<LocalBuiltinParamMetaDto> params) {
        if (params == null || params.isEmpty()) {
            return new McpSchema.JsonSchema("object", Map.of(), List.of(), true, null, null);
        }
        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (LocalBuiltinParamMetaDto p : params) {
            Map<String, Object> spec = new LinkedHashMap<>();
            spec.put("type", jsonType(p.getType()));
            if (p.getDescription() != null && !p.getDescription().isBlank()) {
                spec.put("description", p.getDescription());
            }
            props.put(p.getName(), spec);
            if (p.isRequired()) {
                required.add(p.getName());
            }
        }
        return new McpSchema.JsonSchema("object", props, required, false, null, null);
    }

    private static String jsonType(String javaSimple) {
        if (javaSimple == null || javaSimple.isBlank()) {
            return "string";
        }
        String t = javaSimple.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "int", "integer" -> "integer";
            case "long", "double", "float", "number" -> "number";
            case "boolean" -> "boolean";
            default -> "string";
        };
    }
}
