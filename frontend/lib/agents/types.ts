/**
 * 与后端 Agent DTO / 表单对齐的前端类型（智能体模块单入口）。
 */

import type {AgentRuntimeKind} from "@/lib/agents/runtime-kinds";

/** 创建 / 更新智能体共用表单（与 CreateAgentRequest 一致） */
export type UpsertAgentFormValues = {
    runtimeKind: AgentRuntimeKind;
    maxReactIters?: number;
    name: string;
    systemPrompt: string;
    modelId: string;
    toolIds?: string[];
    memoryPolicyId?: string;
    kbEnabled?: boolean;
    kbCollectionIds?: string[];
    kbTopK?: number;
    kbScoreThreshold?: number;
    kbEmbeddingModelId?: string;
};

export type AgentDto = {
    id: string;
    name: string;
    systemPrompt: string;
    modelId: string;
    modelDisplayName?: string;
    modelProvider?: string;
    modelModelKey?: string;
    modelConfigSummary?: string;
    runtimeKind?: string;
    maxReactIters?: number;
    toolIds?: string[];
    memoryPolicyId?: string;
    memoryPolicyName?: string;
    memoryPolicyOwnerScope?: string;
    knowledgeBasePolicy?: Record<string, unknown>;
    createdAt?: string;
};

export type RunAgentForm = {
    modelId?: string;
    /** 可选：与后端 memoryNamespace 对齐，用于单策略下多用户/会话隔离 */
    memoryNamespace?: string;
    input: string;
    temperature?: number;
    maxTokens?: number;
    topP?: number;
};

export type AgentRunMemoryDebug = {
    memoryPolicyId?: string;
    memoryPolicyName?: string;
    ownerScope?: string;
    retrievalMode?: string;
    writeMode?: string;
    memoryNamespace?: string | null;
    /** 策略 roughSummaryMaxChars 解析后的实际上限（ASSISTANT_SUMMARY 写回时使用） */
    roughSummaryMaxCharsResolved?: number;
    implementationWarnings?: string[];
    /** RECENCY | TRGM_WORD_SIMILARITY */
    keywordPreviewSort?: string;
    previewHitCount?: number;
    previewText?: string;
};

export type RunAgentResponse = {
    output: string;
    memory?: AgentRunMemoryDebug | null;
};
