/**
 * 工具类型等展示用中文文案。
 */
export function toolTypeDisplayName(code: string): string {
    const c = (code ?? "").toUpperCase();
    const map: Record<string, string> = {
        LOCAL: "本地内置",
        MCP: "MCP 外部工具",
        HTTP: "HTTP 请求",
        WORKFLOW: "工作流",
    };
    return map[c] ?? code;
}

/** 新建工具时可选项（与后端 ToolType 一致） */
export const TOOL_TYPE_OPTIONS = [
    {value: "LOCAL", label: "本地内置（进程内 @Tool）"},
    {value: "HTTP", label: "HTTP 请求（可联调）"},
    {value: "MCP", label: "MCP 外部（SSE + 可选入参 Schema）"},
    {value: "WORKFLOW", label: "工作流（可联调，同步执行）"},
] as const;
