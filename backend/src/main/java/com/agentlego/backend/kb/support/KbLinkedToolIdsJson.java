package com.agentlego.backend.kb.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析知识文档 {@code linked_tool_ids} JSON 数组字符串。
 */
public final class KbLinkedToolIdsJson {

    private static final ObjectMapper OM = new ObjectMapper();

    private KbLinkedToolIdsJson() {
    }

    public static List<String> parse(String linkedToolIdsJson) {
        if (linkedToolIdsJson == null || linkedToolIdsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode n = OM.readTree(linkedToolIdsJson);
            List<String> out = new ArrayList<>();
            if (n.isArray()) {
                for (JsonNode x : n) {
                    if (x != null && x.isTextual()) {
                        String t = x.asText().trim();
                        if (!t.isEmpty()) {
                            out.add(t);
                        }
                    }
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }
}
