package com.agentlego.backend.tool.application.support;

import com.agentlego.backend.tool.application.dto.BatchImportMcpToolsRequest;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * MCP 批量导入时平台侧工具名的生成与校验（与前端 NAME_ID_RULES 对齐）。
 */
public final class McpPlatformToolNaming {

    private McpPlatformToolNaming() {
    }

    /**
     * 平台工具 name 规则：字母开头，仅 [A-Za-z0-9_-]。
     */
    public static String sanitizePlatformToolName(String prefix, String remoteName) {
        String p = prefix == null ? "" : prefix.trim();
        String raw = p + Objects.requireNonNull(remoteName, "remoteName").trim();
        String s = raw.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (s.isEmpty()) {
            s = "mcp_tool";
        }
        char c0 = s.charAt(0);
        if (!Character.isLetter(c0)) {
            s = "mcp_" + s;
        }
        return s;
    }

    public static boolean isValidPlatformToolNameForImport(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return name.matches("^[a-zA-Z][a-zA-Z0-9_-]*$");
    }

    public static String resolvePlatformNameForRemote(BatchImportMcpToolsRequest req, String prefix, String remoteName) {
        Map<String, String> overrides = req.getPlatformNamesByRemote();
        if (overrides != null) {
            String v = overrides.get(remoteName);
            if (v != null) {
                return v.trim();
            }
        }
        return sanitizePlatformToolName(prefix, remoteName);
    }

    public static String platformNameLower(String platformName) {
        return platformName.toLowerCase(Locale.ROOT);
    }
}
