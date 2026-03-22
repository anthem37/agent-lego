import {isHttpParameterSchemaFormEditable, rowsFromOutputSchema, rowsFromParametersSchema} from "@/lib/tools/form";
import {kbOutputFieldDescriptionForTool} from "@/lib/tools/http-output-field-description";
import type {HttpParameterRow, ToolDto} from "@/lib/tools/types";
import type {KbDocumentDto} from "@/lib/kb/types";

import {jsonPathToFormKey} from "./doc-render-test";

export type RenderTestFieldOption = { value: string; label: string };

function mappingsFromDoc(doc: KbDocumentDto | null): Array<{ toolId?: string; jsonPath?: string }> {
    const bindings = doc?.toolOutputBindings as { mappings?: unknown[] } | undefined;
    return Array.isArray(bindings?.mappings) ? (bindings.mappings as { toolId?: string; jsonPath?: string }[]) : [];
}

/** 文档里已为该工具配置的占位符绑定 → 出参路径选项 */
export function bindingOutputFieldOptionsForTool(doc: KbDocumentDto | null, toolId: string): RenderTestFieldOption[] {
    const tid = (toolId ?? "").trim();
    if (!tid) {
        return [];
    }
    const seen = new Map<string, string>();
    for (const m of mappingsFromDoc(doc)) {
        if ((m.toolId ?? "").trim() !== tid) {
            continue;
        }
        const jp = (m.jsonPath ?? "").trim();
        const key = jsonPathToFormKey(jp);
        if (!key) {
            continue;
        }
        if (!seen.has(key)) {
            const label = jp && jp !== key ? `${jp} → ${key}` : key;
            seen.set(key, label);
        }
    }
    return [...seen.entries()].map(([value, label]) => ({value, label}));
}

/**
 * 从 HTTP 出参行树收集叶子点路径（与正文里 Cascader 拼接规则一致）。
 */
function collectLeafPathsFromOutputRows(rows: HttpParameterRow[] | undefined, prefix: string[]): string[] {
    if (!rows?.length) {
        return [];
    }
    const out: string[] = [];
    for (const r of rows) {
        const name = (r.paramName ?? "").trim();
        if (!name) {
            continue;
        }
        const segs = [...prefix, name];
        if (r.paramType === "object" && r.children?.length) {
            out.push(...collectLeafPathsFromOutputRows(r.children, segs));
        } else if (r.paramType === "array") {
            const hasObjItems =
                r.arrayItemsPrimitiveType === "object" &&
                Array.isArray(r.arrayItemProperties) &&
                r.arrayItemProperties.some((c) => (c.paramName ?? "").trim().length > 0);
            if (hasObjItems) {
                out.push(...collectLeafPathsFromOutputRows(r.arrayItemProperties!, [...segs, "0"]));
            } else {
                out.push(segs.join("."));
            }
        } else {
            out.push(segs.join("."));
        }
    }
    return out;
}

/** 工具 definition 中 HTTP 出参 schema → 可选路径 */
export function schemaOutputFieldOptionsForTool(tool: ToolDto | undefined | null): RenderTestFieldOption[] {
    if (!tool?.definition || typeof tool.definition !== "object") {
        return [];
    }
    const def = tool.definition as Record<string, unknown>;
    const sch = def.outputSchema;
    if (sch === undefined || !isHttpParameterSchemaFormEditable(sch)) {
        return [];
    }
    const rows = rowsFromOutputSchema(def);
    const paths = [...new Set(collectLeafPathsFromOutputRows(rows, []))];
    return paths.map((value) => {
        const desc = kbOutputFieldDescriptionForTool(tool, value).trim();
        return {
            value,
            label: desc ? `${value} · ${desc}` : value,
        };
    });
}

/**
 * 渲染测试「出参字段」下选项：优先文档绑定，并合并工具出参 schema（HTTP）。
 */
export function buildRenderTestOutputFieldOptions(
    doc: KbDocumentDto | null,
    toolId: string,
    tool: ToolDto | undefined | null,
): RenderTestFieldOption[] {
    const fromBindings = bindingOutputFieldOptionsForTool(doc, toolId);
    const fromSchema = schemaOutputFieldOptionsForTool(tool);
    const map = new Map<string, string>();
    for (const x of fromBindings) {
        map.set(x.value, x.label);
    }
    for (const x of fromSchema) {
        if (!map.has(x.value)) {
            map.set(x.value, x.label);
        }
    }
    return [...map.entries()]
        .map(([value, label]) => ({value, label}))
        .sort((a, b) => a.value.localeCompare(b.value));
}

/**
 * 渲染测试「入参字段」：来自工具 definition.parameters / inputSchema（HTTP/MCP 等）。
 * 与渲染请求无关，仅供对照与填写习惯。
 */
export function buildRenderTestInputFieldOptions(tool: ToolDto | undefined | null): RenderTestFieldOption[] {
    if (!tool?.definition || typeof tool.definition !== "object") {
        return [];
    }
    const def = tool.definition as Record<string, unknown>;
    const rows = rowsFromParametersSchema(def);
    if (!rows.length) {
        return [];
    }
    const paths = [...new Set(collectLeafPathsFromOutputRows(rows, []))];
    return paths
        .map((value) => ({value, label: value}))
        .sort((a, b) => a.value.localeCompare(b.value));
}
