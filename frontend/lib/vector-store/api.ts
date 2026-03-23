import {request, type RequestOptions} from "@/lib/api/request";
import type {
    VectorStoreCollectionStatsDto,
    VectorStoreCollectionSummaryDto,
    VectorStoreEmbeddingProbeResultDto,
    VectorStorePointsPreviewDto,
    VectorStoreProbeResultDto,
    VectorStoreProfileDto,
    VectorStoreUsageDto,
} from "@/lib/vector-store/types";

export type VectorStoreFetchOpts = Pick<RequestOptions, "signal" | "timeoutMs">;

export async function listVectorStoreProfiles(opts?: VectorStoreFetchOpts): Promise<VectorStoreProfileDto[]> {
    const rows = await request<VectorStoreProfileDto[]>("/vector-store-profiles", opts);
    return Array.isArray(rows) ? rows : [];
}

export async function createVectorStoreProfile(
    body: {
        name: string;
        vectorStoreKind: string;
        vectorStoreConfig?: Record<string, unknown>;
        embeddingModelId: string;
    },
    opts?: VectorStoreFetchOpts,
): Promise<VectorStoreProfileDto> {
    return request<VectorStoreProfileDto>("/vector-store-profiles", {method: "POST", body, ...opts});
}

export async function deleteVectorStoreProfile(id: string, opts?: VectorStoreFetchOpts): Promise<void> {
    await request<void>(`/vector-store-profiles/${encodeURIComponent(id)}`, {method: "DELETE", ...opts});
}

/** 连通性 + 版本 + 集合数量 */
export async function probeVectorStoreProfile(id: string, opts?: VectorStoreFetchOpts): Promise<VectorStoreProbeResultDto> {
    return request<VectorStoreProbeResultDto>(`/vector-store-profiles/${encodeURIComponent(id)}/probe`, {...opts});
}

/** 平台内引用该 profile 的知识库集合 */
export async function getVectorStoreUsage(id: string, opts?: VectorStoreFetchOpts): Promise<VectorStoreUsageDto> {
    return request<VectorStoreUsageDto>(`/vector-store-profiles/${encodeURIComponent(id)}/usage`, {...opts});
}

export async function listVectorStoreCollections(
    id: string,
    opts?: VectorStoreFetchOpts,
): Promise<VectorStoreCollectionSummaryDto[]> {
    const rows = await request<VectorStoreCollectionSummaryDto[]>(
        `/vector-store-profiles/${encodeURIComponent(id)}/collections`,
        {...opts},
    );
    return Array.isArray(rows) ? rows : [];
}

export async function getVectorStoreCollectionStats(
    id: string,
    collectionName: string,
    opts?: VectorStoreFetchOpts,
): Promise<VectorStoreCollectionStatsDto> {
    return request<VectorStoreCollectionStatsDto>(
        `/vector-store-profiles/${encodeURIComponent(id)}/collection-stats`,
        {query: {collectionName}, ...opts},
    );
}

/** 仅 Milvus：将 collection 加载到内存 */
export async function loadVectorStoreCollection(
    id: string,
    collectionName: string,
    opts?: VectorStoreFetchOpts,
): Promise<void> {
    await request<void>(`/vector-store-profiles/${encodeURIComponent(id)}/load-collection`, {
        method: "POST",
        query: {collectionName},
        ...opts,
    });
}

/** 删除物理 collection（危险，两次名称须一致） */
export async function dropVectorStoreCollection(
    id: string,
    body: { collectionName: string; confirmCollectionName: string },
    opts?: VectorStoreFetchOpts,
): Promise<void> {
    await request<void>(`/vector-store-profiles/${encodeURIComponent(id)}/drop-collection`, {
        method: "POST",
        body,
        ...opts,
    });
}

/** 用 profile 绑定的嵌入模型对文本做试嵌入，校验维度 */
export async function embeddingProbeVectorStore(
    id: string,
    text: string,
    opts?: VectorStoreFetchOpts,
): Promise<VectorStoreEmbeddingProbeResultDto> {
    return request<VectorStoreEmbeddingProbeResultDto>(
        `/vector-store-profiles/${encodeURIComponent(id)}/embedding-probe`,
        {method: "POST", body: {text}, ...opts},
    );
}

/** Qdrant：scroll 预览点 payload；Milvus 返回 hint */
export async function previewVectorStorePoints(
    profileId: string,
    collectionName: string,
    query?: { limit?: number; cursor?: string | null },
    fetchOpts?: VectorStoreFetchOpts,
): Promise<VectorStorePointsPreviewDto> {
    return request<VectorStorePointsPreviewDto>(
        `/vector-store-profiles/${encodeURIComponent(profileId)}/points-preview`,
        {
            query: {
                collectionName,
                limit: query?.limit ?? 20,
                ...(query?.cursor ? {cursor: query.cursor} : {}),
            },
            ...fetchOpts,
        },
    );
}
