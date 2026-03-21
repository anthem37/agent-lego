package com.agentlego.backend.kb.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将知识正文中的 <code>{{placeholder}}</code> 按集合级绑定替换为工具 JSON 出参字段值。
 */
public final class KbToolPlaceholderExpander {

    private KbToolPlaceholderExpander() {
    }

    /**
     * @param body            文档正文
     * @param bindingsJson    文档持久化的 tool_output_bindings JSON
     * @param toolOutputsById 工具 ID → 该工具一次调用的结果根对象（Map / List / 标量均可）
     */
    public static String expand(
            String body,
            String bindingsJson,
            Map<String, Object> toolOutputsById,
            ObjectMapper om
    ) {
        if (body == null || body.isEmpty()) {
            return body == null ? "" : body;
        }
        if (bindingsJson == null || bindingsJson.isBlank()) {
            return body;
        }
        Map<String, Object> outputs = toolOutputsById == null ? Collections.emptyMap() : toolOutputsById;
        JsonNode root;
        try {
            root = om.readTree(bindingsJson);
        } catch (Exception e) {
            return body;
        }
        JsonNode mappingsNode = root.get("mappings");
        if (mappingsNode == null || !mappingsNode.isArray()) {
            return body;
        }
        String out = body;
        for (JsonNode m : mappingsNode) {
            if (m == null || !m.isObject()) {
                continue;
            }
            JsonNode ph = m.get("placeholder");
            JsonNode tid = m.get("toolId");
            JsonNode jp = m.get("jsonPath");
            if (ph == null || !ph.isTextual() || tid == null || !tid.isTextual() || jp == null || !jp.isTextual()) {
                continue;
            }
            String placeholder = ph.asText();
            String toolId = tid.asText();
            String jsonPath = jp.asText();
            Object raw = outputs.get(toolId);
            JsonNode tree = raw == null ? null : om.valueToTree(raw);
            JsonNode leaf = resolveJsonPath(tree, jsonPath);
            String val = nodeToReplacement(leaf);
            Pattern pat = Pattern.compile("\\{\\{\\s*" + Pattern.quote(placeholder) + "\\s*\\}\\}");
            out = pat.matcher(out).replaceAll(Matcher.quoteReplacement(val));
        }
        return out;
    }

    static JsonNode resolveJsonPath(JsonNode root, String jsonPath) {
        if (root == null || root.isNull()) {
            return null;
        }
        String p = jsonPath == null ? "" : jsonPath.trim();
        if (!p.startsWith("$")) {
            return null;
        }
        if (p.length() == 1) {
            return root;
        }
        if (p.charAt(1) != '.') {
            return null;
        }
        String rest = p.substring(2);
        if (rest.isEmpty()) {
            return root;
        }
        String[] parts = rest.split("\\.");
        JsonNode cur = root;
        for (String part : parts) {
            if (part.isEmpty()) {
                return null;
            }
            if (part.chars().allMatch(Character::isDigit)) {
                int idx = Integer.parseInt(part);
                if (!cur.isArray()) {
                    return null;
                }
                cur = cur.get(idx);
            } else {
                cur = cur.get(part);
            }
            if (cur == null || cur.isNull()) {
                return null;
            }
        }
        return cur;
    }

    private static String nodeToReplacement(JsonNode n) {
        if (n == null || n.isNull()) {
            return "";
        }
        if (n.isTextual()) {
            return n.asText();
        }
        if (n.isNumber() || n.isBoolean()) {
            return n.asText();
        }
        if (n.isArray() || n.isObject()) {
            return n.toString();
        }
        return "";
    }
}
