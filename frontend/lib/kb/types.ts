/**
 * 与后端 KB DTO 对齐；时间字段由 Jackson 序列化为 ISO-8601 字符串。
 */
export type KbCollectionDto = {
    id: string;
    name: string;
    description?: string;
    embeddingModelId: string;
    embeddingDims?: number;
    /** 引用的公共向量库 profile */
    vectorStoreProfileId?: string;
    /** 外置向量库类型：MILVUS | QDRANT */
    vectorStoreKind?: string;
    /** 向量库连接、物理 collection 名等 */
    vectorStoreConfig?: Record<string, unknown>;
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
    /** GET 单条详情时返回，用于编辑回显 */
    similarQueries?: string[];
    createdAt?: string;
    updatedAt?: string;
};

export type KbCollectionDeleteResult = {
    agentsPolicyUpdated: number;
};

export type CreateKbCollectionBody = {
    name: string;
    description?: string;
    /** 可选；须与所选公共向量库 profile 一致，通常可省略 */
    embeddingModelId?: string;
    /** 必填：公共向量库 profile ID */
    vectorStoreProfileId: string;
    vectorStoreKind?: string;
    /** 仅可含 collectionName，用于覆盖物理集合名 */
    vectorStoreConfig?: Record<string, unknown>;
    chunkStrategy?: string;
    chunkParams?: Record<string, unknown>;
};

export type KbValidationIssueDto = {
    severity: string;
    code?: string;
    message?: string;
};

export type KbDocumentValidationResponse = {
    ok: boolean;
    issues: KbValidationIssueDto[];
};

export type KbRetrievePreviewRequest = {
    query: string;
    topK?: number;
    scoreThreshold?: number;
    /** 是否与 RAG 一致地做片段后处理（无会话工具出参） */
    renderSnippets?: boolean;
};

export type KbRetrievePreviewHitDto = {
    chunkId: string;
    /** 命中文档所属集合（多集合召回或单集合也会返回） */
    collectionId?: string | null;
    collectionName?: string | null;
    documentId: string;
    documentTitle?: string;
    score: number;
    content: string;
    renderedContent?: string | null;
    /** 从分片正文解析的相似问（与入库 embedding 块一致） */
    similarQueries?: string[];
};

export type KbRetrievePreviewResponse = {
    query: string;
    hits: KbRetrievePreviewHitDto[];
};

/** POST /kb/retrieve-preview，与智能体多集合 RAG 一致 */
export type KbMultiRetrievePreviewRequest = {
    collectionIds: string[];
    query: string;
    topK?: number;
    scoreThreshold?: number;
    renderSnippets?: boolean;
};

export type KbValidateCollectionDocumentsRequest = {
    /** 为 true 时每条返回完整 issues，集合内文档很多时响应较大 */
    includeIssues?: boolean;
};

export type KbCollectionDocumentValidationItemDto = {
    documentId: string;
    title: string;
    ok: boolean;
    errorCount: number;
    warnCount: number;
    infoCount: number;
    issues?: KbValidationIssueDto[] | null;
};

export type KbCollectionDocumentsValidationResponse = {
    collectionId: string;
    collectionName: string;
    totalDocuments: number;
    documentsOk: number;
    documentsWithErrors: number;
    documentsWithWarningsOnly: number;
    items: KbCollectionDocumentValidationItemDto[];
};

/** GET /kb/meta/agent-policy-summaries */
export type KbAgentPolicySummaryDto = {
    agentId: string;
    agentName: string;
    collectionIds: string[];
};

export type KbRenderDocumentBody = {
    toolOutputs?: Record<string, unknown>;
};

export type KbRenderDocumentResponse = {
    renderedBody: string;
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
