package com.agentlego.backend.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 工具 definition 中的 {@code parameterAliases}：模型调用时使用「参数名」（JSON Schema properties 键），
 * 实际发往 HTTP / MCP 等下游时可映射为另一键名（如 snake_case API）。
 * <p>
 * 约定：{@code parameterAliases} 为对象，键为模型侧参数名，值为运行时键名；省略或空对象表示不做映射。
 */
public final class ParameterAliases {

    private ParameterAliases() {
    }

    public static Map<String, String> parse(Map<String, Object> definition) {
        if (definition == null) {
            return Map.of();
        }
        return parseRaw(definition.get("parameterAliases"));
    }

    public static Map<String, String> parseRaw(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof Map<?, ?> m)) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            String k = String.valueOf(Objects.requireNonNull(e.getKey())).trim();
            Object v = e.getValue();
            if (v == null) {
                continue;
            }
            String w = String.valueOf(v).trim();
            if (k.isEmpty() || w.isEmpty()) {
                continue;
            }
            out.put(k, w);
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * 将模型传入的 input 转为下游使用的键名（根级键重命名；值不变）。
     */
    public static Map<String, Object> toWireInput(Map<String, String> aliases, Map<String, Object> modelInput) {
        if (aliases == null || aliases.isEmpty()) {
            return modelInput == null ? Map.of() : new LinkedHashMap<>(modelInput);
        }
        Map<String, Object> in = modelInput == null ? Map.of() : modelInput;
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : in.entrySet()) {
            String modelKey = e.getKey();
            String wireKey = aliases.getOrDefault(modelKey, modelKey);
            out.put(wireKey, e.getValue());
        }
        return out;
    }

    /**
     * URL 占位符 {@code {key}}：优先取 {@code modelInput.get(key)}；若无，则查找别名为该 key 的模型参数名再取值。
     */
    public static Object resolvePlaceholderValue(Map<String, String> aliases, Map<String, Object> modelInput, String placeholderKey) {
        Map<String, Object> in = modelInput == null ? Map.of() : modelInput;
        Object direct = in.get(placeholderKey);
        if (direct != null) {
            return direct;
        }
        if (aliases == null || aliases.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> e : aliases.entrySet()) {
            if (placeholderKey.equals(e.getValue())) {
                return in.get(e.getKey());
            }
        }
        return null;
    }
}
