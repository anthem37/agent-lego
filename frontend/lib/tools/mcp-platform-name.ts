/**
 * 与后端 {@code ToolApplicationService.sanitizePlatformToolName} 一致，用于批量导入时预览/默认平台工具名。
 */
export function sanitizePlatformToolName(prefix: string, remoteName: string): string {
    const p = (prefix ?? "").trim();
    const raw = `${p}${(remoteName ?? "").trim()}`;
    let s = raw.replace(/[^a-zA-Z0-9_-]/g, "_");
    if (s.length === 0) {
        s = "mcp_tool";
    }
    const c0 = s.charAt(0);
    if (!/[a-zA-Z]/.test(c0)) {
        s = `mcp_${s}`;
    }
    return s;
}

/** 与工具表单 NAME_ID_RULES 一致，便于导入前校验 */
export const PLATFORM_TOOL_NAME_PATTERN = /^[a-zA-Z][a-zA-Z0-9_-]*$/;
