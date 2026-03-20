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
    stream: "流式输出（stream）",
    frequencyPenalty: "频率惩罚（frequencyPenalty）",
    presencePenalty: "存在惩罚（presencePenalty）",
    thinkingBudget: "思考预算（thinkingBudget）",
    reasoningEffort: "推理强度（reasoningEffort）",
    toolChoice: "工具选择策略（toolChoice）",
    executionConfig: "执行与重试（executionConfig）",
    endpointPath: "API 请求路径",
    additionalHeaders: "额外请求头",
    additionalBodyParams: "额外请求体参数",
    additionalQueryParams: "URL 查询参数",
    dimensions: "向量维度（dimensions）",
    encodingFormat: "编码格式（encodingFormat）",
    /** executionConfig 内层字段（与后端白名单一致） */
    timeoutSeconds: "超时（秒）",
    maxAttempts: "最大尝试次数",
    initialBackoffSeconds: "初始退避（秒）",
    maxBackoffSeconds: "最大退避（秒）",
    backoffMultiplier: "退避倍数",
    /** toolChoice 对象内字段 */
    mode: "策略模式",
    toolName: "工具名称",
};

/** 提供方：中文说明 + 保留英文标识便于对照 */
export const PROVIDER_TITLE: Record<string, string> = {
    DASHSCOPE: "阿里云通义（DashScope）",
    OPENAI: "OpenAI 兼容接口",
    ANTHROPIC: "Anthropic（Claude）",
    OPENAI_TEXT_EMBEDDING: "OpenAI 文本嵌入（Embedding）",
    DASHSCOPE_TEXT_EMBEDDING: "通义文本嵌入（Embedding）",
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
