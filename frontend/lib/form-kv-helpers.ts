/**
 * 将表单中的「键 + 文本值」转为后端可用的标量/嵌套对象（支持用点号表示嵌套，如 data.price）。
 * 避免让用户手写 JSON。
 */

/** 将单行文本解析为 string | number | boolean | null（否则原样字符串） */
export function parseScalarFromText(raw: string): unknown {
    const t = raw.trim();
    if (t === "") {
        return "";
    }
    if (t === "true") {
        return true;
    }
    if (t === "false") {
        return false;
    }
    if (t === "null") {
        return null;
    }
    if (/^-?\d+(\.\d+)?([eE][+-]?\d+)?$/.test(t)) {
        const n = Number(t);
        if (!Number.isNaN(n)) {
            return n;
        }
    }
    return raw;
}

/**
 * 扁平键值对 → 嵌套对象；键支持 `a.b.c` 形式。
 */
export function buildNestedObjectFromPairs(pairs: { key: string; value: string }[]): Record<string, unknown> {
    const root: Record<string, unknown> = {};
    for (const {key, value} of pairs) {
        const k = key.trim();
        if (!k) {
            continue;
        }
        const parts = k.split(".").map((p) => p.trim());
        if (parts.some((p) => !p)) {
            continue;
        }
        let cur: Record<string, unknown> = root;
        for (let i = 0; i < parts.length - 1; i++) {
            const p = parts[i];
            const next = cur[p];
            if (next === null || typeof next !== "object" || Array.isArray(next)) {
                cur[p] = {};
            }
            cur = cur[p] as Record<string, unknown>;
        }
        cur[parts[parts.length - 1]] = parseScalarFromText(value);
    }
    return root;
}

/** 知识库渲染测试：多工具 → 每工具一块键值（键可用 a.b 表示嵌套） */
export function buildToolOutputsFromBlocks(
    blocks: { toolId: string; pairs: { key: string; value: string }[] }[],
): Record<string, unknown> {
    const out: Record<string, unknown> = {};
    for (const block of blocks ?? []) {
        const tid = (block.toolId ?? "").trim();
        if (!tid) {
            continue;
        }
        out[tid] = buildNestedObjectFromPairs(block.pairs ?? []);
    }
    return out;
}

/** 记忆 metadata：仅一层键值（不嵌套） */
export function buildFlatMetadataFromPairs(pairs: { key: string; value: string }[]): Record<string, unknown> {
    const out: Record<string, unknown> = {};
    for (const {key, value} of pairs) {
        const k = key.trim();
        if (!k) {
            continue;
        }
        out[k] = parseScalarFromText(value);
    }
    return out;
}
