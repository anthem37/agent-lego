/**
 * 将表单中的 config 对象整理为可提交给后端的结构（去掉空值、仅保留允许的 key）。
 */
export function normalizeModelConfig(
    raw: Record<string, unknown> | undefined,
    supportedKeys: string[],
): Record<string, unknown> | undefined {
    if (!raw || typeof raw !== "object") {
        return undefined;
    }
    const out: Record<string, unknown> = {};

    for (const key of supportedKeys) {
        if (!(key in raw)) {
            continue;
        }
        const v = raw[key];
        if (v === undefined || v === null) {
            continue;
        }
        if (typeof v === "string" && v.trim() === "") {
            continue;
        }
        if (key === "additionalHeaders" || key === "additionalBodyParams" || key === "additionalQueryParams") {
            if (typeof v !== "object" || v === null || Array.isArray(v)) {
                continue;
            }
            const obj = v as Record<string, unknown>;
            const entries = Object.entries(obj).filter(([k, val]) => k.trim() !== "" && val !== undefined && val !== null && String(val) !== "");
            if (entries.length === 0) {
                continue;
            }
            out[key] = Object.fromEntries(entries.map(([k, val]) => [k.trim(), val]));
            continue;
        }
        out[key] = v;
    }

    return Object.keys(out).length > 0 ? out : undefined;
}

export type StringPair = { key: string; value: string };

export function objectToPairs(obj: unknown): StringPair[] {
    if (!obj || typeof obj !== "object" || Array.isArray(obj)) {
        return [];
    }
    const o = obj as Record<string, unknown>;
    return Object.entries(o).map(([key, value]) => ({
        key,
        value: value === undefined || value === null ? "" : String(value),
    }));
}

export function pairsToObject(pairs: StringPair[]): Record<string, string> {
    const out: Record<string, string> = {};
    for (const p of pairs) {
        const k = p.key.trim();
        if (!k) {
            continue;
        }
        out[k] = p.value ?? "";
    }
    return out;
}
