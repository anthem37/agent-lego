package com.agentlego.backend.tool.schema;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolOutputSchemaDescriptionTest {

    @Test
    void appendToDescription_appendsFieldsAndRequiredMarker() {
        StringBuilder sb = new StringBuilder("Base");
        Map<String, Object> userIdProp = Map.of(
                "type", "string",
                "description", "用户标识"
        );
        ToolOutputSchemaDescription.appendToDescription(sb, Map.of(
                "type", "object",
                "properties", Map.of("userId", userIdProp),
                "required", List.of("userId")
        ));
        String s = sb.toString();
        assertTrue(s.startsWith("Base"));
        assertTrue(s.contains("【返回说明】"));
        assertTrue(s.contains("userId"));
        assertTrue(s.contains("[必有]"));
        assertTrue(s.contains("用户标识"));
    }

    @Test
    void appendToDescription_ignoresNullAndEmptyProperties() {
        StringBuilder sb = new StringBuilder("X");
        ToolOutputSchemaDescription.appendToDescription(sb, null);
        assertTrue(sb.toString().equals("X"));
    }
}
