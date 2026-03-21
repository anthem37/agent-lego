import type {DefaultOptionType} from "antd/es/cascader";

import type {HttpParameterRow} from "@/lib/tools/types";

/** 出参类型在级联中的中文展示（技术值不变，仅 label） */
function schemaTypeLabelZh(raw: string | undefined): string {
    const k = String(raw ?? "")
        .trim()
        .toLowerCase();
    const map: Record<string, string> = {
        string: "字符串",
        number: "数字",
        integer: "整数",
        boolean: "布尔",
        object: "对象",
        array: "数组",
        item: "元素",
        null: "空值",
        any: "任意",
    };
    return map[k] || String(raw ?? "").trim() || "未知";
}

/**
 * 将工具管理里「HTTP 出参表格」同一结构的 {@link HttpParameterRow} 树转为 Ant Design Cascader 的 options。
 * 选中值用 `.` 拼接即为占位路径（不含 `$`），与 {@link rowsFromOutputSchema} 一致。
 */
export function httpOutputRowsToCascaderOptions(rows: HttpParameterRow[] | undefined): DefaultOptionType[] {
    if (!rows?.length) {
        return [];
    }
    const out: DefaultOptionType[] = [];
    for (const r of rows) {
        const name = (r.paramName ?? "").trim();
        if (!name) {
            continue;
        }

        if (r.paramType === "object") {
            const children = httpOutputRowsToCascaderOptions(r.children);
            const typeZh = schemaTypeLabelZh("object");
            if (children.length > 0) {
                out.push({value: name, label: `${name} · ${typeZh}`, children});
            } else {
                out.push({value: name, label: `${name} · ${typeZh}`});
            }
            continue;
        }

        if (r.paramType === "array") {
            const itemProps = r.arrayItemProperties;
            const hasObjItems =
                r.arrayItemsPrimitiveType === "object" &&
                Array.isArray(itemProps) &&
                itemProps.some((c) => (c.paramName ?? "").trim().length > 0);
            if (hasObjItems) {
                const elChildren = httpOutputRowsToCascaderOptions(itemProps);
                if (elChildren.length > 0) {
                    out.push({
                        value: name,
                        label: `${name} · 对象数组`,
                        children: [{value: "0", label: "[0] 首元素", children: elChildren}],
                    });
                } else {
                    out.push({value: name, label: `${name} · ${schemaTypeLabelZh("array")}`});
                }
                continue;
            }
            const prim = r.arrayItemsPrimitiveType ?? "item";
            out.push({value: name, label: `${name} · ${schemaTypeLabelZh(prim)}数组`});
            continue;
        }

        out.push({value: name, label: `${name} · ${schemaTypeLabelZh(r.paramType)}`});
    }
    return out;
}
