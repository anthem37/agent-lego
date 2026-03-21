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
    /** GET 单条时若有富文本入库则有值 */
    bodyRich?: string;
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

export type IngestKbDocumentBody = {
    title: string;
    /**
     * 富文本 HTML；非空时服务端<strong>仅据此</strong>转 Markdown 后入库与向量化（控制台主路径）。
     */
    bodyRich?: string;
    /** 仅兼容旧客户端直传 Markdown；与 bodyRich 同时存在时 body 会被忽略 */
    body?: string;
    /** 可选，拼入每条分片的 embedding 输入（最多 32 条，每条最长 512 字符） */
    similarQueries?: string[];
    /** 本条知识绑定的工具 ID */
    linkedToolIds?: string[];
    toolOutputBindings?: Record<string, unknown>;
};
