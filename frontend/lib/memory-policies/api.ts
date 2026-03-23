import {request, type RequestOptions} from "@/lib/api/request";

/** 与 {@link request} 对齐的可选中止与超时，供列表/详情取消过时请求。 */
export type MemoryPolicyFetchOpts = Pick<RequestOptions, "signal" | "timeoutMs">;

export type MemoryPolicyDto = {
    id: string;
    name: string;
    description?: string;
    ownerScope: string;
    strategyKind?: string;
    scopeKind?: string;
    retrievalMode?: string;
    topK?: number;
    writeMode?: string;
    writeBackOnDuplicate?: string;
    /** ASSISTANT_SUMMARY 时本地粗略摘要最大字符数；未设置时后端按默认 480 */
    roughSummaryMaxChars?: number;
    /** 绑定该策略的智能体数量 */
    referencingAgentCount?: number;
    /** 与当前实现能力相关的提示（VECTOR 降级、ASSISTANT_SUMMARY 等） */
    implementationWarnings?: string[];
    vectorStoreProfileId?: string;
    vectorStoreConfig?: Record<string, unknown>;
    vectorMinScore?: number;
    /** VECTOR/HYBRID 时是否已配置可检索的外置向量链路 */
    vectorLinkConfigured?: boolean | null;
    createdAt?: string;
    updatedAt?: string;
};

export type AgentRefDto = {
    id: string;
    name: string;
};

export type MemoryItemDto = {
    id: string;
    policyId: string;
    content: string;
    metadata?: Record<string, unknown>;
    createdAt?: string;
    updatedAt?: string;
};

export async function listMemoryPolicies(opts?: MemoryPolicyFetchOpts): Promise<MemoryPolicyDto[]> {
    return request<MemoryPolicyDto[]>("/memory-policies", {...opts});
}

export async function getMemoryPolicy(id: string, opts?: MemoryPolicyFetchOpts): Promise<MemoryPolicyDto> {
    return request<MemoryPolicyDto>(`/memory-policies/${encodeURIComponent(id)}`, {...opts});
}

export async function listReferencingAgents(policyId: string, opts?: MemoryPolicyFetchOpts): Promise<AgentRefDto[]> {
    return request<AgentRefDto[]>(
        `/memory-policies/${encodeURIComponent(policyId)}/referencing-agents`,
        {...opts},
    );
}

export type MemoryPolicyUpsertBody = {
    name: string;
    description?: string;
    ownerScope: string;
    strategyKind?: string;
    scopeKind?: string;
    retrievalMode?: string;
    topK?: number;
    writeMode?: string;
    writeBackOnDuplicate?: "skip" | "upsert";
    roughSummaryMaxChars?: number;
    /** 仅 PUT：为 true 时清空库中上限，运行时使用默认 480 */
    clearRoughSummaryMaxChars?: boolean;
    vectorStoreProfileId?: string;
    vectorStoreConfig?: Record<string, unknown>;
    vectorMinScore?: number;
    /** 仅 PUT：清空向量库绑定与配置 */
    clearVectorLink?: boolean;
};

export async function createMemoryPolicy(body: MemoryPolicyUpsertBody, opts?: MemoryPolicyFetchOpts): Promise<string> {
    return request<string>("/memory-policies", {
        method: "POST",
        body,
        ...opts,
    });
}

export async function updateMemoryPolicy(
    id: string,
    body: MemoryPolicyUpsertBody,
    opts?: MemoryPolicyFetchOpts,
): Promise<void> {
    await request<void>(`/memory-policies/${encodeURIComponent(id)}`, {
        method: "PUT",
        body,
        ...opts,
    });
}

export async function deleteMemoryPolicy(id: string, opts?: MemoryPolicyFetchOpts): Promise<void> {
    await request<void>(`/memory-policies/${encodeURIComponent(id)}`, {
        method: "DELETE",
        ...opts,
    });
}

export type MemoryReindexVectorsResultDto = {
    indexedCount: number;
};

/** VECTOR/HYBRID 且向量链路已配置时才会实际写入索引；否则后端可能返回 indexedCount=0 */
export async function reindexMemoryPolicyVectors(
    policyId: string,
    opts?: MemoryPolicyFetchOpts,
): Promise<MemoryReindexVectorsResultDto> {
    return request<MemoryReindexVectorsResultDto>(
        `/memory-policies/${encodeURIComponent(policyId)}/reindex-vectors`,
        {method: "POST", ...opts},
    );
}

export async function listMemoryItems(
    policyId: string,
    params?: {
        q?: string;
        limit?: number;
        /** 与智能体运行时一致：有关键词时按 pg_trgm word_similarity 排序 */ orderByTrgm?: boolean
    },
    opts?: MemoryPolicyFetchOpts,
): Promise<MemoryItemDto[]> {
    return request<MemoryItemDto[]>(`/memory-policies/${encodeURIComponent(policyId)}/items`, {
        query: {
            ...(params?.q ? {q: params.q} : {}),
            ...(params?.limit != null ? {limit: params.limit} : {}),
            ...(params?.orderByTrgm === true ? {orderByTrgm: true} : {}),
        },
        ...opts,
    });
}

export async function createMemoryItem(
    policyId: string,
    body: { content: string; metadata?: Record<string, unknown> },
    opts?: MemoryPolicyFetchOpts,
): Promise<string> {
    return request<string>(`/memory-policies/${encodeURIComponent(policyId)}/items`, {
        method: "POST",
        body,
        ...opts,
    });
}

export async function deleteMemoryItem(policyId: string, itemId: string, opts?: MemoryPolicyFetchOpts): Promise<void> {
    await request<void>(
        `/memory-policies/${encodeURIComponent(policyId)}/items/${encodeURIComponent(itemId)}`,
        {
            method: "DELETE",
            ...opts,
        },
    );
}
