/** 列表/详情 Tag 颜色（与类型一一对应，新增类型时在此扩展）。 */
export function toolTypeTagColor(t: string): string {
    const c = (t ?? "").toUpperCase();
    const map: Record<string, string> = {
        LOCAL: "blue",
        MCP: "geekblue",
        HTTP: "green",
        WORKFLOW: "purple",
    };
    return map[c] ?? "default";
}
