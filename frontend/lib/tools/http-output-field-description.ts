import {isHttpParameterSchemaFormEditable, rowsFromOutputSchema} from "@/lib/tools/form";
import type {HttpParameterRow, ToolDto} from "@/lib/tools/types";

function pathSegments(fieldDotPath: string): string[] {
    return String(fieldDotPath ?? "")
        .trim()
        .replace(/^\$\.?/, "")
        .split(".")
        .map((s) => s.trim())
        .filter(Boolean);
}

/**
 * 在 HTTP 出参行树中，按点分路径（与级联 value 拼接一致）找到叶子行，返回 `paramDescription`。
 * 支持 `items.0.name` 这类数组下标段（与 {@link httpOutputRowsToCascaderOptions} 一致）。
 */
export function resolveHttpOutputFieldDescription(rows: HttpParameterRow[] | undefined, fieldDotPath: string): string {
    const segs = pathSegments(fieldDotPath);
    if (!rows?.length || segs.length === 0) {
        return "";
    }
    const leaf = walkOutputRowsForLeaf(rows, segs, 0);
    const d = leaf?.paramDescription?.trim();
    return d ?? "";
}

function walkOutputRowsForLeaf(
    rows: HttpParameterRow[],
    segs: string[],
    i: number,
): HttpParameterRow | undefined {
    if (i >= segs.length || !rows?.length) {
        return undefined;
    }
    const key = segs[i] ?? "";
    const row = rows.find((r) => (r.paramName ?? "").trim() === key);
    if (!row) {
        return undefined;
    }
    if (i === segs.length - 1) {
        return row;
    }

    const next = segs[i + 1] ?? "";
    if (row.paramType === "object" && row.children?.length) {
        return walkOutputRowsForLeaf(row.children, segs, i + 1);
    }
    if (row.paramType === "array") {
        const itemProps = row.arrayItemProperties;
        const hasObjItems =
            row.arrayItemsPrimitiveType === "object" &&
            Array.isArray(itemProps) &&
            itemProps.some((c) => (c.paramName ?? "").trim().length > 0);
        if (hasObjItems && /^\d+$/.test(next)) {
            return walkOutputRowsForLeaf(itemProps!, segs, i + 2);
        }
    }
    return undefined;
}

/** 从工具 definition 的 outputSchema 解析某路径的字段说明 */
export function kbOutputFieldDescriptionForTool(t: ToolDto | undefined | null, fieldDotPath: string): string {
    if (!t?.definition || typeof t.definition !== "object") {
        return "";
    }
    const def = t.definition as Record<string, unknown>;
    const outSch = def.outputSchema;
    if (outSch === undefined || !isHttpParameterSchemaFormEditable(outSch)) {
        return "";
    }
    const rows = rowsFromOutputSchema(def);
    return resolveHttpOutputFieldDescription(rows, fieldDotPath);
}
