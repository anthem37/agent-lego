const EXECUTION_CONFIG_KEYS = [
    "timeoutSeconds",
    "maxAttempts",
    "initialBackoffSeconds",
    "maxBackoffSeconds",
    "backoffMultiplier",
] as const;

/**
 * 规范化 AgentScope `GenerateOptions.executionConfig` 子对象。
 */
export function normalizeExecutionConfig(raw: unknown): Record<string, unknown> | undefined {
    if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
        return undefined;
    }
    const o = raw as Record<string, unknown>;
    const out: Record<string, unknown> = {};
    for (const k of EXECUTION_CONFIG_KEYS) {
        if (!(k in o)) {
            continue;
        }
        const v = o[k];
        if (v === undefined || v === null) {
            continue;
        }
        if (typeof v === "string" && v.trim() === "") {
            continue;
        }
        out[k] = v;
    }
    return Object.keys(out).length > 0 ? out : undefined;
}

/**
 * 规范化 AgentScope `GenerateOptions.toolChoice`（字符串或对象）。
 */
export function normalizeToolChoice(raw: unknown): string | Record<string, string> | undefined {
    if (raw === undefined || raw === null) {
        return undefined;
    }
    if (typeof raw === "string") {
        const t = raw.trim().toLowerCase();
        if (!t) {
            return undefined;
        }
        if (t === "auto" || t === "none" || t === "required") {
            return t;
        }
        return undefined;
    }
    if (typeof raw === "object" && !Array.isArray(raw)) {
        const o = raw as Record<string, unknown>;
        const toolName = typeof o.toolName === "string" ? o.toolName.trim() : "";
        if (toolName) {
            return {mode: "specific", toolName};
        }
        const mode = typeof o.mode === "string" ? o.mode.trim().toLowerCase() : "";
        if (mode === "auto" || mode === "none" || mode === "required") {
            return mode;
        }
    }
    return undefined;
}

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
        if (key === "executionConfig") {
            const n = normalizeExecutionConfig(v);
            if (n) {
                out[key] = n;
            }
            continue;
        }
        if (key === "toolChoice") {
            const n = normalizeToolChoice(v);
            if (n !== undefined) {
                out[key] = n;
            }
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
