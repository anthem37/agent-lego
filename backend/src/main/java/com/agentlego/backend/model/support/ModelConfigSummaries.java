package com.agentlego.backend.model.support;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将模型 config 压缩成短文本，用于列表/下拉中区分「同模型不同参数」的配置实例。
 */
public final class ModelConfigSummaries {

    private static final int MAX_LEN = 160;

    private ModelConfigSummaries() {
    }

    /**
     * 生成简短摘要（优先展示常见推理参数；过长则截断）。
     */
    public static String summarize(Map<String, Object> config) {
        if (MapUtil.isEmpty(config)) {
            return "";
        }
        Map<String, String> ordered = new LinkedHashMap<>();
        putIfPresent(ordered, "temperature", config.get("temperature"));
        putIfPresent(ordered, "topP", config.get("topP"));
        putIfPresent(ordered, "topK", config.get("topK"));
        putIfPresent(ordered, "maxTokens", config.get("maxTokens"));
        putIfPresent(ordered, "maxCompletionTokens", config.get("maxCompletionTokens"));
        putIfPresent(ordered, "seed", config.get("seed"));
        putIfPresent(ordered, "endpointPath", config.get("endpointPath"));
        // 其余 key 按字典序少量附带，避免摘要爆炸
        for (Map.Entry<String, Object> e : config.entrySet()) {
            if (ordered.containsKey(e.getKey())) {
                continue;
            }
            if (e.getKey().startsWith("additional")) {
                continue;
            }
            putIfPresent(ordered, e.getKey(), e.getValue());
        }
        if (config.containsKey("additionalHeaders") && isNonEmptyObject(config.get("additionalHeaders"))) {
            ordered.put("headers", "+" + objectSize(config.get("additionalHeaders")) + "项");
        }
        if (config.containsKey("additionalBodyParams") && isNonEmptyObject(config.get("additionalBodyParams"))) {
            ordered.put("body+", "+" + objectSize(config.get("additionalBodyParams")) + "项");
        }
        if (config.containsKey("additionalQueryParams") && isNonEmptyObject(config.get("additionalQueryParams"))) {
            ordered.put("query+", "+" + objectSize(config.get("additionalQueryParams")) + "项");
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : ordered.entrySet()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(e.getKey()).append("=").append(e.getValue());
            if (sb.length() >= MAX_LEN) {
                break;
            }
        }
        if (sb.length() > MAX_LEN) {
            return sb.substring(0, MAX_LEN - 1) + "…";
        }
        return sb.toString();
    }

    private static void putIfPresent(Map<String, String> out, String key, Object value) {
        if (value == null) {
            return;
        }
        String s = stringify(value);
        if (StrUtil.isBlank(s)) {
            return;
        }
        out.put(key, s);
    }

    private static String stringify(Object value) {
        if (value instanceof String s) {
            return s.length() > 48 ? StrUtil.sub(s, 0, 45) + "…" : s;
        }
        return StrUtil.toString(value);
    }

    private static boolean isNonEmptyObject(Object v) {
        return v instanceof Map<?, ?> m && !m.isEmpty();
    }

    private static int objectSize(Object v) {
        return v instanceof Map<?, ?> m ? m.size() : 0;
    }
}
