import {request} from "@/lib/api/request";
import type {ModelOptionRow} from "@/lib/model-select-options";

import type {ModelDetail, ModelDto, ModelSummary, ProviderMeta, TestModelResponse,} from "@/lib/models/types";

export async function listModelProviders(signal?: AbortSignal): Promise<ProviderMeta[]> {
    const data = await request<ProviderMeta[]>("/models/providers", {signal});
    return Array.isArray(data) ? data : [];
}

export async function listModels(signal?: AbortSignal): Promise<ModelSummary[]> {
    const data = await request<ModelSummary[]>("/models", {signal});
    return Array.isArray(data) ? data : [];
}

/**
 * 下拉选择用：与 {@link ModelOptionRow} 对齐（GET /models 与创建页/向量库页一致）。
 */
export async function listModelsAsSelectRows(signal?: AbortSignal): Promise<ModelOptionRow[]> {
    const data = await request<ModelOptionRow[]>("/models", {signal});
    return Array.isArray(data) ? data : [];
}

export async function getModelDetail(id: string, signal?: AbortSignal): Promise<ModelDetail> {
    return request<ModelDetail>(`/models/${encodeURIComponent(id)}`, {signal});
}

export async function getModel(id: string, signal?: AbortSignal): Promise<ModelDto> {
    return request<ModelDto>(`/models/${encodeURIComponent(id)}`, {signal});
}

export async function createModel(body: Record<string, unknown>, signal?: AbortSignal): Promise<string> {
    return request<string>("/models", {method: "POST", body, signal});
}

export async function updateModel(id: string, body: Record<string, unknown>, signal?: AbortSignal): Promise<void> {
    await request<void>(`/models/${encodeURIComponent(id)}`, {method: "PUT", body, signal});
}

export async function deleteModel(id: string, signal?: AbortSignal): Promise<void> {
    await request<void>(`/models/${encodeURIComponent(id)}`, {method: "DELETE", signal});
}

export async function testModel(
    id: string,
    body: Record<string, unknown>,
    signal?: AbortSignal,
): Promise<TestModelResponse> {
    return request<TestModelResponse>(`/models/${encodeURIComponent(id)}/test`, {
        method: "POST",
        body,
        signal,
    });
}
