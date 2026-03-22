import {request} from "@/lib/api/request";

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
    /** 绑定该策略的智能体数量 */
    referencingAgentCount?: number;
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

export async function listMemoryPolicies(): Promise<MemoryPolicyDto[]> {
    return request<MemoryPolicyDto[]>("/memory-policies");
}

export async function getMemoryPolicy(id: string): Promise<MemoryPolicyDto> {
    return request<MemoryPolicyDto>(`/memory-policies/${encodeURIComponent(id)}`);
}

export async function listReferencingAgents(policyId: string): Promise<AgentRefDto[]> {
    return request<AgentRefDto[]>(
        `/memory-policies/${encodeURIComponent(policyId)}/referencing-agents`,
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
};

export async function createMemoryPolicy(body: MemoryPolicyUpsertBody): Promise<string> {
    return request<string>("/memory-policies", {
        method: "POST",
        body,
    });
}

export async function updateMemoryPolicy(id: string, body: MemoryPolicyUpsertBody): Promise<void> {
    await request<void>(`/memory-policies/${encodeURIComponent(id)}`, {
        method: "PUT",
        body,
    });
}

export async function deleteMemoryPolicy(id: string): Promise<void> {
    await request<void>(`/memory-policies/${encodeURIComponent(id)}`, {
        method: "DELETE",
    });
}

export async function listMemoryItems(
    policyId: string,
    params?: { q?: string; limit?: number },
): Promise<MemoryItemDto[]> {
    return request<MemoryItemDto[]>(`/memory-policies/${encodeURIComponent(policyId)}/items`, {
        query: {
            ...(params?.q ? {q: params.q} : {}),
            ...(params?.limit != null ? {limit: params.limit} : {}),
        },
    });
}

export async function createMemoryItem(
    policyId: string,
    body: { content: string; metadata?: Record<string, unknown> },
): Promise<string> {
    return request<string>(`/memory-policies/${encodeURIComponent(policyId)}/items`, {
        method: "POST",
        body,
    });
}

export async function deleteMemoryItem(policyId: string, itemId: string): Promise<void> {
    await request<void>(
        `/memory-policies/${encodeURIComponent(policyId)}/items/${encodeURIComponent(itemId)}`,
        {
            method: "DELETE",
        },
    );
}
