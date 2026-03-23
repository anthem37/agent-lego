"use client";

import React from "react";

import {DEFAULT_REQUEST_TIMEOUT_MS} from "@/lib/api/request";
import {
    buildCollectionSelectOptions,
    buildMemoryPolicySelectOptions,
    buildToolSelectOptions,
} from "@/lib/agents/form-options";
import {listMemoryPolicies, type MemoryPolicyDto} from "@/lib/memory-policies/api";
import {listKbCollections} from "@/lib/kb/api";
import type {KbCollectionDto} from "@/lib/kb/types";
import {listModelsAsSelectRows} from "@/lib/models/api";
import {type ModelOptionRow} from "@/lib/model-select-options";
import {listToolsPage} from "@/lib/tools/api";
import type {ToolDto} from "@/lib/tools/types";

export type AgentFormRefsState = {
    modelRows: ModelOptionRow[];
    tools: ToolDto[];
    collections: KbCollectionDto[];
    memoryPolicies: MemoryPolicyDto[];
    loadingRefs: boolean;
    chatModelRows: ModelOptionRow[];
    embeddingModelRows: ModelOptionRow[];
    toolOptions: ReturnType<typeof buildToolSelectOptions>;
    collectionOptions: ReturnType<typeof buildCollectionSelectOptions>;
    memoryPolicyOptions: ReturnType<typeof buildMemoryPolicySelectOptions>;
};

/**
 * 智能体创建/编辑页共用的模型、工具、知识库集合、记忆策略加载与派生选项。
 */
export function useAgentFormRefs(): AgentFormRefsState {
    const [modelRows, setModelRows] = React.useState<ModelOptionRow[]>([]);
    const [tools, setTools] = React.useState<ToolDto[]>([]);
    const [collections, setCollections] = React.useState<KbCollectionDto[]>([]);
    const [memoryPolicies, setMemoryPolicies] = React.useState<MemoryPolicyDto[]>([]);
    const [loadingRefs, setLoadingRefs] = React.useState(true);

    React.useEffect(() => {
        const ac = new AbortController();
        const opts = {signal: ac.signal, timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS};
        setLoadingRefs(true);
        void (async () => {
            try {
                const [models, toolPage, cols, memPols] = await Promise.all([
                    listModelsAsSelectRows(opts),
                    listToolsPage({page: 1, pageSize: 200}, opts),
                    listKbCollections(opts),
                    listMemoryPolicies(opts).catch(() => [] as MemoryPolicyDto[]),
                ]);
                if (!ac.signal.aborted) {
                    setModelRows(models);
                    setTools(Array.isArray(toolPage.items) ? toolPage.items : []);
                    setCollections(Array.isArray(cols) ? cols : []);
                    setMemoryPolicies(Array.isArray(memPols) ? memPols : []);
                }
            } catch {
                if (!ac.signal.aborted) {
                    setModelRows([]);
                    setTools([]);
                    setCollections([]);
                    setMemoryPolicies([]);
                }
            } finally {
                if (!ac.signal.aborted) {
                    setLoadingRefs(false);
                }
            }
        })();
        return () => {
            ac.abort();
        };
    }, []);

    const chatModelRows = React.useMemo(
        () => modelRows.filter((m) => m.chatProvider !== false),
        [modelRows],
    );
    const embeddingModelRows = React.useMemo(
        () => modelRows.filter((m) => m.chatProvider === false),
        [modelRows],
    );

    const toolOptions = React.useMemo(() => buildToolSelectOptions(tools), [tools]);
    const collectionOptions = React.useMemo(() => buildCollectionSelectOptions(collections), [collections]);
    const memoryPolicyOptions = React.useMemo(
        () => buildMemoryPolicySelectOptions(memoryPolicies),
        [memoryPolicies],
    );

    return {
        modelRows,
        tools,
        collections,
        memoryPolicies,
        loadingRefs,
        chatModelRows,
        embeddingModelRows,
        toolOptions,
        collectionOptions,
        memoryPolicyOptions,
    };
}
