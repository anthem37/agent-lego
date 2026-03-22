import {request} from "@/lib/api/request";
import type {
    VectorStoreCollectionStatsDto,
    VectorStoreCollectionSummaryDto,
    VectorStoreEmbeddingProbeResultDto,
    VectorStorePointsPreviewDto,
    VectorStoreProbeResultDto,
    VectorStoreProfileDto,
    VectorStoreUsageDto,
} from "@/lib/vector-store/types";

export async function listVectorStoreProfiles(): Promise<VectorStoreProfileDto[]> {
    const rows = await request<VectorStoreProfileDto[]>("/vector-store-profiles");
    return Array.isArray(rows) ? rows : [];
}

export async function createVectorStoreProfile(body: {
    name: string;
    vectorStoreKind: string;
    vectorStoreConfig?: Record<string, unknown>;
    embeddingModelId: string;
}): Promise<VectorStoreProfileDto> {
    return request<VectorStoreProfileDto>("/vector-store-profiles", {method: "POST", body});
}

export async function deleteVectorStoreProfile(id: string): Promise<void> {
    await request<void>(`/vector-store-profiles/${encodeURIComponent(id)}`, {method: "DELETE"});
}

/** 连通性 + 版本 + 集合数量 */
export async function probeVectorStoreProfile(id: string): Promise<VectorStoreProbeResultDto> {
    return request<VectorStoreProbeResultDto>(`/vector-store-profiles/${encodeURIComponent(id)}/probe`);
}

/** 平台内引用该 profile 的知识库集合 */
export async function getVectorStoreUsage(id: string): Promise<VectorStoreUsageDto> {
    return request<VectorStoreUsageDto>(`/vector-store-profiles/${encodeURIComponent(id)}/usage`);
}

export async function listVectorStoreCollections(id: string): Promise<VectorStoreCollectionSummaryDto[]> {
    const rows = await request<VectorStoreCollectionSummaryDto[]>(
        `/vector-store-profiles/${encodeURIComponent(id)}/collections`,
    );
    return Array.isArray(rows) ? rows : [];
}

export async function getVectorStoreCollectionStats(
    id: string,
    collectionName: string,
): Promise<VectorStoreCollectionStatsDto> {
    return request<VectorStoreCollectionStatsDto>(
        `/vector-store-profiles/${encodeURIComponent(id)}/collection-stats`,
        {query: {collectionName}},
    );
}

/** 仅 Milvus：将 collection 加载到内存 */
export async function loadVectorStoreCollection(id: string, collectionName: string): Promise<void> {
    await request<void>(`/vector-store-profiles/${encodeURIComponent(id)}/load-collection`, {
        method: "POST",
        query: {collectionName},
    });
}

/** 删除物理 collection（危险，两次名称须一致） */
export async function dropVectorStoreCollection(
    id: string,
    body: { collectionName: string; confirmCollectionName: string },
): Promise<void> {
    await request<void>(`/vector-store-profiles/${encodeURIComponent(id)}/drop-collection`, {
        method: "POST",
        body,
    });
}

/** 用 profile 绑定的嵌入模型对文本做试嵌入，校验维度 */
export async function embeddingProbeVectorStore(
    id: string,
    text: string,
): Promise<VectorStoreEmbeddingProbeResultDto> {
    return request<VectorStoreEmbeddingProbeResultDto>(
        `/vector-store-profiles/${encodeURIComponent(id)}/embedding-probe`,
        {method: "POST", body: {text}},
    );
}

/** Qdrant：scroll 预览点 payload；Milvus 返回 hint */
export async function previewVectorStorePoints(
    profileId: string,
    collectionName: string,
    opts?: { limit?: number; cursor?: string | null },
): Promise<VectorStorePointsPreviewDto> {
    return request<VectorStorePointsPreviewDto>(
        `/vector-store-profiles/${encodeURIComponent(profileId)}/points-preview`,
        {
            query: {
                collectionName,
                limit: opts?.limit ?? 20,
                ...(opts?.cursor ? {cursor: opts.cursor} : {}),
            },
        },
    );
}
