/** 知识库（空间配置），对应后端 KbBaseDto */
export type KbBaseDto = {
    id: string;
    kbKey: string;
    name: string;
    description?: string | null;
    createdAt?: string;
    documentCount: number;
    lastIngestAt?: string | null;
};

export type CreateKbBaseBody = {
    kbKey: string;
    name: string;
    description?: string;
};

export type UpdateKbBaseBody = {
    name: string;
    description?: string;
};

/** 与后端 chunk_strategy 一致 */
export type KbChunkStrategy = "fixed" | "paragraph" | "hybrid" | "markdown_sections";

/** 知识（文档），对应后端 KbDocumentSummaryDto */
export type KbDocumentSummaryDto = {
    id: string;
    baseId: string;
    kbKey: string;
    name: string;
    /** markdown | html */
    contentFormat?: string;
    /** 入库时选择的分片策略 */
    chunkStrategy?: KbChunkStrategy | string;
    chunkCount: number;
    createdAt?: string;
};

/** 知识详情（含全文） */
export type KbKnowledgeDetailDto = {
    id: string;
    baseId: string;
    kbKey: string;
    name: string;
    contentRich?: string | null;
    contentFormat?: string;
    chunkStrategy?: KbChunkStrategy | string;
    createdAt?: string;
};

export type KbDocumentPageDto = {
    items: KbDocumentSummaryDto[];
    total: number;
    page: number;
    pageSize: number;
};

export type KbIngestResponse = {
    documentId: string;
    chunkCount: number;
};

export type KbChunkDto = {
    id: string;
    documentId: string;
    documentName?: string;
    chunkIndex: number;
    content: string;
    metadata?: Record<string, unknown>;
    createdAt?: string;
};

export type KbQueryResponse = {
    chunks: KbChunkDto[];
};

/** 添加知识（不含知识库 id，由路径指定） */
export type CreateKnowledgeBody = {
    name: string;
    content: string;
    /** markdown | html，缺省由后端视为 markdown */
    contentFormat?: "markdown" | "html";
    /** 缺省 fixed，与后端一致 */
    chunkStrategy?: KbChunkStrategy;
    chunkSize: number;
    overlap: number;
};

export type KbQueryBody = {
    baseId?: string;
    kbKey?: string;
    queryText: string;
    topK: number;
};
