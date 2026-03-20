package com.agentlego.backend.common;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JSON/Map 访问辅助，委托 Hutool MapUtil + Jackson。
 * <p>
 * 避免重复造轮子，统一从 jsonb/Map 读取字段。
 */
public final class JsonMaps {
    private static final ObjectMapper OM = JacksonHolder.INSTANCE;

    private JsonMaps() {
    }

    public static Map<String, Object> asMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> raw) {
            return raw.entrySet().stream()
                    .collect(Collectors.toMap(e -> StrUtil.toString(e.getKey()), e -> (Object) e.getValue()));
        }
        return Map.of();
    }

    public static String getString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null || key == null) {
            return defaultValue;
        }
        String s = MapUtil.getStr(map, key, defaultValue);
        return StrUtil.isBlank(s) ? defaultValue : s;
    }

    public static double getDouble(Map<String, Object> map, String key, double defaultValue) {
        if (map == null || key == null) {
            return defaultValue;
        }
        Double v = MapUtil.getDouble(map, key, null);
        return v != null ? v : defaultValue;
    }

    public static int getInt(Map<String, Object> map, String key, int defaultValue) {
        if (map == null || key == null) {
            return defaultValue;
        }
        Integer v = MapUtil.getInt(map, key, null);
        return v != null ? v : defaultValue;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMap(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return Map.of();
        }
        Object v = map.get(key);
        if (!(v instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        return MapUtil.isEmpty(raw) ? Map.of() : asMap(v);
    }

    public static Map<String, String> getStringMap(Map<String, Object> map, String key) {
        Map<String, Object> raw = getMap(map, key);
        if (MapUtil.isEmpty(raw)) {
            return Map.of();
        }
        return raw.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .collect(Collectors.toMap(e -> StrUtil.toString(e.getKey()), e -> StrUtil.toString(e.getValue())));
    }

    public static Integer getIntOpt(Map<String, Object> map, String key) {
        return map == null || key == null ? null : MapUtil.getInt(map, key, null);
    }

    public static Long getLongOpt(Map<String, Object> map, String key) {
        return map == null || key == null ? null : MapUtil.getLong(map, key, null);
    }

    public static Double getDoubleOpt(Map<String, Object> map, String key) {
        return map == null || key == null ? null : MapUtil.getDouble(map, key, null);
    }

    public static List<Map<String, Object>> getListOfMaps(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return List.of();
        }
        Object v = map.get(key);
        if (!(v instanceof List<?> list) || CollUtil.isEmpty(list)) {
            return List.of();
        }
        return list.stream().map(JsonMaps::asMap).filter(m -> !MapUtil.isEmpty(m)).toList();
    }

    public static Map<String, Object> parseObject(String json) {
        if (StrUtil.isBlank(json)) {
            return Map.of();
        }
        try {
            return asMap(OM.readValue(json, new TypeReference<Map<String, Object>>() {
            }));
        } catch (Exception e) {
            return Map.of();
        }
    }

    public static String toJson(Map<String, Object> map) {
        Map<String, Object> safe = map == null ? Collections.emptyMap() : map;
        try {
            return OM.writeValueAsString(safe);
        } catch (Exception e) {
            return "{}";
        }
    }
}
