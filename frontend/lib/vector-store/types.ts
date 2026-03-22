/** 与后端 VectorStoreProfileDto 对齐 */
export type VectorStoreProfileDto = {
    id: string;
    name: string;
    vectorStoreKind: string;
    vectorStoreConfig?: Record<string, unknown>;
    embeddingModelId: string;
    embeddingDims: number;
    createdAt?: string;
    updatedAt?: string;
};

export type VectorStoreProbeResultDto = {
    ok: boolean;
    message?: string;
    latencyMs: number;
    serverVersion?: string;
    healthSummary?: string;
    collectionCount?: number;
};

export type VectorStoreCollectionSummaryDto = {
    name: string;
    loadedPercent?: number;
    queryServiceAvailable?: boolean;
};

export type VectorStoreCollectionStatsDto = {
    collectionName: string;
    rowCount?: number | null;
    rawStats?: Record<string, string>;
};

export type VectorStoreUsageDto = {
    kbCollectionCount: number;
    kbCollections: { id: string; name: string; physicalCollectionName?: string }[];
};

export type VectorStoreEmbeddingProbeResultDto = {
    ok: boolean;
    embeddingModelId: string;
    vectorDimension: number;
    dimensionMatchesProfile: boolean;
    vectorNorm: number;
    message?: string;
};

/** Qdrant scroll 点数据预览；Milvus 时 hint 有说明 */
export type VectorStorePointPreviewRowDto = {
    id?: string;
    payload?: Record<string, unknown>;
};

export type VectorStorePointsPreviewDto = {
    collectionName: string;
    rows: VectorStorePointPreviewRowDto[];
    nextCursor?: string | null;
    hint?: string | null;
};
