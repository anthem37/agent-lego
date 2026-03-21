package com.agentlego.backend.tool.domain;

import java.util.Locale;

/**
 * 工具语义分类（与 {@link ToolType} 执行方式正交）。
 * <p>
 * QUERY：偏只读查询，知识库可将出参字段映射到文档占位符；ACTION：操作类或默认分类。
 */
public enum ToolCategory {
    QUERY,
    ACTION;

    /**
     * 持久化层读取；未知值降级为 ACTION，避免脏数据导致启动/查询失败。
     */
    public static ToolCategory fromStorage(String raw) {
        if (raw == null || raw.isBlank()) {
            return ACTION;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ACTION;
        }
    }
}
