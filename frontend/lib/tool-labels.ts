/**
 * 工具类型等展示用中文文案。
 */
export function toolTypeDisplayName(code: string): string {
    const c = (code ?? "").toUpperCase();
    if (c === "LOCAL") {
        return "本地内置";
    }
    if (c === "MCP") {
        return "MCP 外部工具";
    }
    return code;
}
