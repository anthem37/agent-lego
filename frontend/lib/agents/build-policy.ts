/**
 * 将表单值组装为后端 CreateAgentRequest 中的 knowledgeBasePolicy（与 KbPolicies、AgentApplicationService 字段对齐）。
 */

import {AGENT_RUNTIME} from "@/lib/agents/runtime-kinds";
import type {UpsertAgentFormValues} from "@/lib/agents/types";

export type KbPolicyForm = {
    kbEnabled?: boolean;
    kbCollectionIds?: string[];
    kbTopK?: number;
    kbScoreThreshold?: number;
    kbEmbeddingModelId?: string;
};

/**
 * 将智能体快照中的 knowledgeBasePolicy 还原为表单初值（用于编辑页）。
 */
export function parseKbPolicyFromAgent(policy: Record<string, unknown> | undefined): KbPolicyForm {
    if (!policy || typeof policy !== "object") {
        return {kbEnabled: false};
    }
    const raw = policy.collectionIds;
    const arr = Array.isArray(raw) ? raw.filter((x): x is string => typeof x === "string") : [];
    if (arr.length === 0) {
        return {kbEnabled: false};
    }
    return {
        kbEnabled: true,
        kbCollectionIds: arr,
        kbTopK: typeof policy.topK === "number" ? policy.topK : 5,
        kbScoreThreshold: typeof policy.scoreThreshold === "number" ? policy.scoreThreshold : 0.25,
        kbEmbeddingModelId: typeof policy.embeddingModelId === "string" ? policy.embeddingModelId : undefined,
    };
}

export function buildKnowledgeBasePolicy(v: KbPolicyForm): Record<string, unknown> | undefined {
    if (!v.kbEnabled) {
        return undefined;
    }
    const ids = (v.kbCollectionIds ?? []).map((x) => x.trim()).filter(Boolean);
    if (ids.length === 0) {
        return undefined;
    }
    const out: Record<string, unknown> = {
        collectionIds: ids,
        topK: typeof v.kbTopK === "number" && v.kbTopK > 0 ? v.kbTopK : 5,
        scoreThreshold:
            typeof v.kbScoreThreshold === "number" && !Number.isNaN(v.kbScoreThreshold)
                ? v.kbScoreThreshold
                : 0.25,
    };
    const emb = (v.kbEmbeddingModelId ?? "").trim();
    if (emb) {
        out.embeddingModelId = emb;
    }
    return out;
}

/** 运行期 options：仅非空字段写入，与模型 config 合并 */
export function buildRunOptions(parts: {
    temperature?: number;
    maxTokens?: number;
    topP?: number;
}): Record<string, unknown> | undefined {
    const o: Record<string, unknown> = {};
    if (typeof parts.temperature === "number" && !Number.isNaN(parts.temperature)) {
        o.temperature = parts.temperature;
    }
    if (typeof parts.maxTokens === "number" && parts.maxTokens > 0) {
        o.maxTokens = parts.maxTokens;
    }
    if (typeof parts.topP === "number" && !Number.isNaN(parts.topP)) {
        o.topP = parts.topP;
    }
    return Object.keys(o).length > 0 ? o : undefined;
}

/**
 * 创建 / 更新智能体共用的请求体（与 {@link UpsertAgentFormValues} + 后端 CreateAgentRequest 对齐）。
 */
export function buildUpsertAgentRequestBody(
    values: UpsertAgentFormValues,
    isReactRuntime: boolean,
): Record<string, unknown> {
    const knowledgeBasePolicy = buildKnowledgeBasePolicy(values);
    const kind = values.runtimeKind ?? AGENT_RUNTIME.REACT;
    const mid = values.memoryPolicyId?.trim();
    return {
        name: values.name.trim(),
        systemPrompt: values.systemPrompt,
        modelId: values.modelId,
        toolIds: isReactRuntime && values.toolIds?.length ? values.toolIds : [],
        runtimeKind: kind,
        ...(isReactRuntime && typeof values.maxReactIters === "number"
            ? {maxReactIters: values.maxReactIters}
            : {}),
        ...(mid ? {memoryPolicyId: mid} : {}),
        ...(knowledgeBasePolicy ? {knowledgeBasePolicy} : {}),
    };
}
