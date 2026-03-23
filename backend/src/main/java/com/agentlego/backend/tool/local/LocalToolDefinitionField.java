package com.agentlego.backend.tool.local;

/**
 * LOCAL 工具 {@code definition} JSON 字段名（与 HTTP 工具的 parameters/outputSchema 展示对齐）。
 */
public enum LocalToolDefinitionField {

    INPUT_SCHEMA("inputSchema"),
    OUTPUT_SCHEMA("outputSchema"),
    DESCRIPTION("description");

    private final String jsonKey;

    LocalToolDefinitionField(String jsonKey) {
        this.jsonKey = jsonKey;
    }

    public String jsonKey() {
        return jsonKey;
    }
}
