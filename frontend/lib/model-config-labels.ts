/**
 * 模型配置与元数据的中文展示文案（API 字段名仍为英文，界面以中文为主）。
 */

/** config 字段：中文名称（主标题），供表单与详情共用 */
export const CONFIG_KEY_TITLE: Record<string, string> = {
    temperature: "采样温度",
    topP: "Top‑P 核采样",
    topK: "Top‑K 候选数",
    maxTokens: "最大输出长度（tokens）",
    maxCompletionTokens: "补全最大长度（tokens）",
    seed: "随机种子",
    endpointPath: "API 请求路径",
    additionalHeaders: "额外请求头",
    additionalBodyParams: "额外请求体参数",
    additionalQueryParams: "URL 查询参数",
};

/** 提供方：中文说明 + 保留英文标识便于对照 */
export const PROVIDER_TITLE: Record<string, string> = {
    DASHSCOPE: "阿里云通义（DashScope）",
    OPENAI: "OpenAI 兼容接口",
    ANTHROPIC: "Anthropic（Claude）",
};

export function configKeyTitle(key: string): string {
    return CONFIG_KEY_TITLE[key] ?? key;
}

export function providerDisplayName(code: string): string {
    const upper = code.trim().toUpperCase();
    return PROVIDER_TITLE[upper] ?? code;
}

export function formatScalarForDisplay(value: unknown): string {
    if (value === undefined || value === null) {
        return "—";
    }
    if (typeof value === "number" || typeof value === "boolean") {
        return String(value);
    }
    if (typeof value === "string") {
        return value.length > 0 ? value : "—";
    }
    return JSON.stringify(value);
}
