package com.agentlego.backend.kb.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 校验并规范化<strong>单条知识文档</strong>的「工具出参 → 占位符」绑定 JSON。
 */
public final class KbDocumentToolBindings {

    private static final int MAX_MAPPINGS = 64;
    private static final Pattern PLACEHOLDER_KEY = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,63}$");

    private KbDocumentToolBindings() {
    }

    /**
     * 将请求体中的 toolOutputBindings（可为 null）规范为持久化 JSON 字符串。
     *
     * @throws IllegalArgumentException 校验失败
     */
    public static String normalizeBindingsJson(Map<String, Object> raw, ObjectMapper om) throws IllegalArgumentException {
        if (raw == null || raw.isEmpty()) {
            return defaultBindingsJson();
        }
        JsonNode root = om.valueToTree(raw);
        if (!root.isObject()) {
            throw new IllegalArgumentException("toolOutputBindings 必须是 JSON 对象");
        }
        ArrayNode mappingsIn = null;
        JsonNode m = root.get("mappings");
        if (m != null && !m.isNull()) {
            if (!m.isArray()) {
                throw new IllegalArgumentException("toolOutputBindings.mappings 必须是数组");
            }
            mappingsIn = (ArrayNode) m;
        }
        if (mappingsIn == null) {
            return defaultBindingsJson();
        }
        if (mappingsIn.size() > MAX_MAPPINGS) {
            throw new IllegalArgumentException("toolOutputBindings.mappings 最多 " + MAX_MAPPINGS + " 条");
        }
        ArrayNode outArr = om.createArrayNode();
        for (JsonNode item : mappingsIn) {
            if (item == null || !item.isObject()) {
                throw new IllegalArgumentException("mappings 每项必须是对象");
            }
            String placeholder = textField(item, "placeholder");
            String toolId = textField(item, "toolId");
            String jsonPath = textField(item, "jsonPath");
            if (placeholder.isEmpty()) {
                throw new IllegalArgumentException("mappings[].placeholder 不能为空");
            }
            if (!PLACEHOLDER_KEY.matcher(placeholder).matches()) {
                throw new IllegalArgumentException(
                        "占位符 key 须匹配 [a-zA-Z][a-zA-Z0-9_]{0,63}，正文使用 {{" + placeholder + "}}"
                );
            }
            if (toolId.isEmpty()) {
                throw new IllegalArgumentException("mappings[].toolId 不能为空");
            }
            if (jsonPath.isEmpty()) {
                throw new IllegalArgumentException("mappings[].jsonPath 不能为空");
            }
            String jp = jsonPath.trim();
            if (!jp.startsWith("$")) {
                throw new IllegalArgumentException("jsonPath 须以 $ 开头，例如 $.data.orderNo");
            }
            ObjectNode one = om.createObjectNode();
            one.put("placeholder", placeholder);
            one.put("toolId", toolId.trim());
            one.put("jsonPath", jp);
            outArr.add(one);
        }
        ObjectNode out = om.createObjectNode();
        out.set("mappings", outArr);
        try {
            return om.writeValueAsString(out);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法序列化 toolOutputBindings");
        }
    }

    public static String defaultBindingsJson() {
        return "{\"mappings\":[]}";
    }

    private static String textField(JsonNode obj, String field) {
        JsonNode n = obj.get(field);
        if (n == null || n.isNull()) {
            return "";
        }
        if (!n.isTextual()) {
            return "";
        }
        return n.asText().trim();
    }

    /**
     * 规范化关联工具 ID 列表为 JSON 数组字符串；去重、保序。
     */
    public static String normalizeLinkedToolIdsJson(List<String> ids) throws IllegalArgumentException {
        if (ids == null || ids.isEmpty()) {
            return "[]";
        }
        if (ids.size() > 32) {
            throw new IllegalArgumentException("linkedToolIds 最多 32 个");
        }
        List<String> cleaned = new ArrayList<>();
        LinkedHashMap<String, Boolean> seen = new LinkedHashMap<>();
        for (String id : ids) {
            if (id == null) {
                continue;
            }
            String t = id.trim();
            if (t.isEmpty()) {
                throw new IllegalArgumentException("linkedToolIds 含空项");
            }
            if (t.length() > 64) {
                throw new IllegalArgumentException("linkedToolIds 单项过长");
            }
            String key = t.toLowerCase(Locale.ROOT);
            if (seen.putIfAbsent(key, Boolean.TRUE) == null) {
                cleaned.add(t);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < cleaned.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(escapeJson(cleaned.get(i))).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
