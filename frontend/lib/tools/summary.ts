import type {ToolDto} from "@/lib/tools/types";

const URL_MAX = 52;
const HTTP_METHODS_WITH_JSON_BODY = new Set(["POST", "PUT", "PATCH"]);

const MAX_COUNT_SCHEMA_DEPTH = 14;

function asObj(v: unknown): Record<string, unknown> | null {
    return v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : null;
}

/** 统计 schema 中「可展示字段」数量：含嵌套 object 属性与 array&lt;object&gt; 内属性（与详情页展平一致） */
function countNestedSchemaFields(raw: unknown, depth: number): number {
    if (depth > MAX_COUNT_SCHEMA_DEPTH) {
        return 0;
    }
    const schema = asObj(raw);
    if (!schema) {
        return 0;
    }
    const props = schema.properties;
    if (!props || typeof props !== "object" || Array.isArray(props)) {
        return 0;
    }
    const map = props as Record<string, unknown>;
    let n = 0;
    for (const name of Object.keys(map)) {
        n += 1;
        const s = asObj(map[name]);
        if (!s) {
            continue;
        }
        const childProps = s.properties;
        if (childProps && typeof childProps === "object" && !Array.isArray(childProps)) {
            n += countNestedSchemaFields({type: "object", properties: childProps}, depth + 1);
        }
        const items = s.items;
        if (items && typeof items === "object" && !Array.isArray(items)) {
            const im = asObj(items);
            if (im?.properties && typeof im.properties === "object" && !Array.isArray(im.properties)) {
                n += countNestedSchemaFields({type: "object", properties: im.properties}, depth + 1);
            }
        }
    }
    return n;
}

function countHttpParameterProps(def: Record<string, unknown>): number {
    return countNestedSchemaFields(def.parameters ?? def.inputSchema, 0);
}

function countHttpOutputProps(def: Record<string, unknown>): number {
    return countNestedSchemaFields(def.outputSchema, 0);
}

function trunc(s: string, max: number): string {
    if (s.length <= max) {
        return s;
    }
    return `${s.slice(0, Math.max(0, max - 1))}…`;
}

/**
 * 列表/检索用：从 definition 生成一行可读摘要（非 JSON）。
 */
export function summarizeToolDefinition(tool: ToolDto): string {
    const t = (tool.toolType ?? "").toUpperCase();
    const def = tool.definition ?? {};

    if (t === "HTTP") {
        const method = typeof def.method === "string" ? def.method.toUpperCase() : "GET";
        const url = typeof def.url === "string" ? def.url : "";
        const base = url ? `${method} ${trunc(url, URL_MAX)}` : `${method}（未配置 URL）`;
        const hdrs = def.headers;
        const hn =
            hdrs && typeof hdrs === "object" && !Array.isArray(hdrs)
                ? Object.keys(hdrs as Record<string, unknown>).length
                : 0;
        const pn = countHttpParameterProps(def);
        const on = countHttpOutputProps(def);
        const parts = [base];
        if (pn > 0) {
            parts.push(`${pn} 个入参`);
        }
        if (on > 0) {
            parts.push(`${on} 个出参`);
        }
        if (hn > 0) {
            parts.push(`${hn} 个请求头`);
        }
        if (HTTP_METHODS_WITH_JSON_BODY.has(method) && def.sendJsonBody === false) {
            parts.push("不发 JSON body");
        }
        return parts.length > 1 ? parts.join(" · ") : base;
    }

    if (t === "WORKFLOW") {
        const id = def.workflowId != null ? String(def.workflowId) : "";
        return id ? `工作流 ${trunc(id, 48)}` : "工作流（未填 workflowId）";
    }

    if (t === "MCP") {
        const ep = typeof def.endpoint === "string" ? def.endpoint : "";
        const remote = typeof def.mcpToolName === "string" && def.mcpToolName.trim() ? def.mcpToolName.trim() : null;
        const pn = countHttpParameterProps(def);
        const parts: string[] = [];
        parts.push(ep ? trunc(ep, 48) : "未填端点");
        if (remote) {
            parts.push(`远端:${remote}`);
        }
        if (pn > 0) {
            parts.push(`${pn} 个入参`);
        }
        return parts.join(" · ");
    }

    if (t === "LOCAL") {
        const d = typeof def.description === "string" ? def.description.trim() : "";
        return d ? trunc(d, 64) : `内置 · ${tool.name}`;
    }

    const d = typeof def.description === "string" ? def.description.trim() : "";
    const keys = Object.keys(def);
    if (d) {
        return trunc(d, 64);
    }
    if (keys.length > 0) {
        return `已配置 ${keys.length} 个字段`;
    }
    return "—";
}
