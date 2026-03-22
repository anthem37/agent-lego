/**
 * 与后端模型管理 API 对齐的类型（控制台「模型」模块）。
 */

export type ModelSummary = {
    id: string;
    name: string;
    description?: string;
    provider: string;
    modelKey: string;
    baseUrl?: string;
    configSummary?: string;
    createdAt?: string;
};

export type ModelDetail = ModelSummary & {
    config?: Record<string, unknown>;
    apiKeyConfigured?: boolean;
};

/** GET /models/{id} 详情页快照 */
export type ModelDto = {
    id: string;
    name: string;
    description?: string;
    provider: string;
    modelKey: string;
    baseUrl?: string;
    config?: Record<string, unknown>;
    createdAt?: string;
    apiKeyConfigured?: boolean;
};

export type ModelFormValues = {
    name: string;
    description?: string;
    provider?: string;
    modelKey: string;
    apiKey?: string;
    baseUrl?: string;
    config?: Record<string, unknown>;
};

export type ProviderMeta = {
    provider: string;
    supportedConfigKeys: string[];
    chatProvider?: boolean;
    modelKind?: string;
};

export type TestModelResponse = {
    testType?: string;
    status?: string;
    latencyMs?: number;
    streamChunks?: number;
    promptUsed?: string;
    maxTokensUsed?: number;
    message: string;
    raw: string;
    embeddingDimension?: number;
    embeddingPreview?: string;
};
