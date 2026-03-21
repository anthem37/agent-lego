/**
 * 与后端 KB DTO 对齐；时间字段由 Jackson 序列化为 ISO-8601 字符串。
 */
export type KbCollectionDto = {
    id: string;
    name: string;
    description?: string;
    embeddingModelId: string;
    embeddingDims?: number;
    chunkStrategy?: string;
    chunkParams?: Record<string, unknown>;
    createdAt?: string;
    updatedAt?: string;
};

/** 与 GET /kb/meta/chunk-strategies 对齐 */
export type KbChunkStrategyMetaDto = {
    value: string;
    label: string;
    description: string;
    defaultParams: Record<string, unknown>;
};

export type KbDocumentDto = {
    id: string;
    collectionId: string;
    title: string;
    /** GET 单条详情时有值；列表接口为减轻负载不返回 */
    body?: string;
    status: string;
    errorMessage?: string;
    /** 本条知识绑定的工具 */
    linkedToolIds?: string[];
    toolOutputBindings?: Record<string, unknown>;
    createdAt?: string;
    updatedAt?: string;
};

export type KbCollectionDeleteResult = {
    agentsPolicyUpdated: number;
};

export type CreateKbCollectionBody = {
    name: string;
    description?: string;
    embeddingModelId: string;
    chunkStrategy?: string;
    chunkParams?: Record<string, unknown>;
};

export type RenderKbDocumentBody = {
    toolOutputs?: Record<string, unknown>;
};

export type RenderKbDocumentResult = {
    renderedBody: string;
};

export type IngestKbDocumentBody = {
    title: string;
    body: string;
    /** 可选，拼入每条分片的 embedding 输入（最多 32 条，每条最长 512 字符） */
    similarQueries?: string[];
    /** 本条知识绑定的工具 ID */
    linkedToolIds?: string[];
    toolOutputBindings?: Record<string, unknown>;
};
