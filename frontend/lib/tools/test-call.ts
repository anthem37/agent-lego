import {extractHttpUrlPlaceholderNames, rowsFromParametersSchema} from "@/lib/tools/form";

import type {LocalBuiltinToolMetaDto, ToolDto} from "@/lib/tools/types";

/** 工具详情页「联调」表单的参数行 */
export type TestCallParamRow = {
    paramName: string;
    paramValue: string;
};

/**
 * 根据工具类型与 definition / 内置元数据，生成联调表单的默认参数行（参数名预填，取值留空由用户填写）。
 */
export function buildDefaultTestCallParamRows(
    tool: ToolDto,
    options?: {localBuiltinMeta?: LocalBuiltinToolMetaDto},
): TestCallParamRow[] {
    const t = tool.toolType.toUpperCase();

    if (t === "LOCAL") {
        const inputs = options?.localBuiltinMeta?.inputParameters;
        if (inputs && inputs.length > 0) {
            return inputs.map((p) => ({
                paramName: p.name,
                paramValue: "",
            }));
        }
        return [{paramName: "", paramValue: ""}];
    }

    if (t === "HTTP") {
        const def = tool.definition ?? {};
        const rec = def as Record<string, unknown>;
        const httpRows = rowsFromParametersSchema(rec);
        const named = httpRows.map((r) => (r.paramName ?? "").trim()).filter(Boolean);
        if (named.length > 0) {
            return named.map((paramName) => ({paramName, paramValue: ""}));
        }
        const url = typeof rec.url === "string" ? rec.url : "";
        const placeholders = extractHttpUrlPlaceholderNames(url);
        if (placeholders.length > 0) {
            return placeholders.map((paramName) => ({paramName, paramValue: ""}));
        }
        return [{paramName: "", paramValue: ""}];
    }

    if (t === "WORKFLOW") {
        return [{paramName: "input", paramValue: ""}];
    }

    if (t === "MCP") {
        const rec = tool.definition ?? {};
        const httpRows = rowsFromParametersSchema(rec as Record<string, unknown>);
        const named = httpRows.map((r) => (r.paramName ?? "").trim()).filter(Boolean);
        if (named.length > 0) {
            return named.map((paramName) => ({paramName, paramValue: ""}));
        }
        return [{paramName: "", paramValue: ""}];
    }

    return [{paramName: "", paramValue: ""}];
}
