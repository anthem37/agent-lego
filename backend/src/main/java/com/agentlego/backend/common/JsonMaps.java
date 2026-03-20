package com.agentlego.backend.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JSON/Map 访问辅助工具。
 * <p>
 * 设计目标：
 * - 统一处理从 jsonb / Map<String, Object> 中读取字段时的类型不确定性；
 * - 避免散落在各处的强制类型转换与魔法字符串，提升可读性与健壮性。
 * <p>
 * 说明：该类只做“无副作用”的转换与读取；业务校验应在 Application/Domain 层完成。
 */
public final class JsonMaps {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonMaps() {
    }

    /**
     * 尝试把任意对象转换成 Map（常用于 json 反序列化后的 LinkedHashMap）。
     * 若转换失败则返回空 Map。
     */
    public static Map<String, Object> asMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> raw) {
            // normalize keys to String
            return raw.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            e -> String.valueOf(e.getKey()),
                            e -> (Object) e.getValue()
                    ));
        }
        return Map.of();
    }

    /**
     * 从 map 读取 String。
     */
    public static String getString(Map<String, Object> map, String key, String defaultValue) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(key, "key");
        Object v = map.get(key);
        if (v == null) {
            return defaultValue;
        }
        String s = String.valueOf(v);
        return s.isBlank() ? defaultValue : s;
    }

    /**
     * 从 map 读取 int（支持 Number / String）。
     */
    public static int getInt(Map<String, Object> map, String key, int defaultValue) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(key, "key");
        Object v = map.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    /**
     * 读取 List<Map<String, Object>>，并把每个元素的 key 归一化成 String。
     */
    public static List<Map<String, Object>> getListOfMaps(Map<String, Object> map, String key) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(key, "key");
        Object v = map.get(key);
        if (!(v instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(JsonMaps::asMap)
                .filter(m -> !m.isEmpty())
                .toList();
    }

    /**
     * JSON -> Map（用于持久化层读取 jsonb/varchar）。
     */
    public static Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            return asMap(raw);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * Map -> JSON（用于持久化层写入）。
     */
    public static String toJson(Map<String, Object> map) {
        Map<String, Object> safe = map == null ? Collections.emptyMap() : map;
        try {
            return OBJECT_MAPPER.writeValueAsString(safe);
        } catch (Exception e) {
            return "{}";
        }
    }
}

