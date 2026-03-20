package com.agentlego.backend.tool.schema;

import java.util.*;

/**
 * 将 definition 中的 {@code outputSchema}（JSON Schema 子集）格式化为自然语言，追加到 {@link io.agentscope.core.tool.AgentTool#getDescription()}。
 * <p>
 * AgentScope 工具模型无独立「出参」字段，故通过 description 让模型理解返回结构；运行时不对真实输出做校验。
 */
public final class ToolOutputSchemaDescription {

    private ToolOutputSchemaDescription() {
    }

    @SuppressWarnings("unchecked")
    public static void appendToDescription(StringBuilder sb, Object outputSchema) {
        if (!(outputSchema instanceof Map<?, ?> root)) {
            return;
        }
        Map<String, Object> schema = (Map<String, Object>) root;
        Object propsObj = schema.get("properties");
        if (!(propsObj instanceof Map<?, ?> propsRaw) || propsRaw.isEmpty()) {
            return;
        }
        sb.append("\n\n【返回说明】工具返回多为文本（常为 JSON）；逻辑上可包含字段：\n");
        Set<String> required = new HashSet<>();
        Object reqArr = schema.get("required");
        if (reqArr instanceof List<?> list) {
            for (Object x : list) {
                if (x != null) {
                    required.add(String.valueOf(x));
                }
            }
        }
        List<String> names = new ArrayList<>();
        for (Object k : propsRaw.keySet()) {
            if (k != null) {
                names.add(String.valueOf(k));
            }
        }
        names.sort(String::compareTo);
        for (String name : names) {
            Object specObj = propsRaw.get(name);
            sb.append("- ").append(name);
            if (specObj instanceof Map<?, ?> spec) {
                Object type = spec.get("type");
                Object desc = spec.get("description");
                if (type != null) {
                    sb.append(" (").append(type).append(")");
                }
                if (desc != null && !String.valueOf(desc).isBlank()) {
                    sb.append("：").append(String.valueOf(desc).trim());
                }
            }
            if (required.contains(name)) {
                sb.append(" [必有]");
            }
            sb.append("\n");
        }
    }
}
