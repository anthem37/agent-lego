export function parseJsonObject(text: string): Record<string, unknown> {
    const trimmed = text.trim();
    if (!trimmed) {
        return {};
    }
    const parsed = JSON.parse(trimmed) as unknown;
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
        throw new Error("需要是 JSON 对象（object），例如 {\"a\":1}");
    }
    return parsed as Record<string, unknown>;
}

export function parseJsonValue(text: string): unknown {
    const trimmed = text.trim();
    if (!trimmed) {
        return null;
    }
    return JSON.parse(trimmed) as unknown;
}

export function parseJsonArray(text: string): unknown[] {
    const value = parseJsonValue(text);
    if (!Array.isArray(value)) {
        throw new Error("需要是 JSON 数组（array），例如 [{\"a\":1}]");
    }
    return value;
}

export function stringifyPretty(value: unknown): string {
    if (value === undefined || value === null) {
        return "";
    }
    try {
        return JSON.stringify(value, null, 2);
    } catch {
        return String(value);
    }
}

