import {request, type RequestOptions} from "@/lib/api/request";
import type {ModelOptionRow} from "@/lib/model-select-options";

import type {ModelDetail, ModelDto, ModelSummary, ProviderMeta, TestModelResponse,} from "@/lib/models/types";

export type ModelFetchOpts = Pick<RequestOptions, "signal" | "timeoutMs">;

export async function listModelProviders(opts?: ModelFetchOpts): Promise<ProviderMeta[]> {
    const data = await request<ProviderMeta[]>("/models/providers", {...opts});
    return Array.isArray(data) ? data : [];
}

export async function listModels(opts?: ModelFetchOpts): Promise<ModelSummary[]> {
    const data = await request<ModelSummary[]>("/models", {...opts});
    return Array.isArray(data) ? data : [];
}

/**
 * 下拉选择用：与 {@link ModelOptionRow} 对齐（GET /models 与创建页/向量库页一致）。
 */
export async function listModelsAsSelectRows(opts?: ModelFetchOpts): Promise<ModelOptionRow[]> {
    const data = await request<ModelOptionRow[]>("/models", {...opts});
    return Array.isArray(data) ? data : [];
}

export async function getModelDetail(id: string, opts?: ModelFetchOpts): Promise<ModelDetail> {
    return request<ModelDetail>(`/models/${encodeURIComponent(id)}`, {...opts});
}

export async function getModel(id: string, opts?: ModelFetchOpts): Promise<ModelDto> {
    return request<ModelDto>(`/models/${encodeURIComponent(id)}`, {...opts});
}

export async function createModel(body: Record<string, unknown>, opts?: ModelFetchOpts): Promise<string> {
    return request<string>("/models", {method: "POST", body, ...opts});
}

export async function updateModel(id: string, body: Record<string, unknown>, opts?: ModelFetchOpts): Promise<void> {
    await request<void>(`/models/${encodeURIComponent(id)}`, {method: "PUT", body, ...opts});
}

export async function deleteModel(id: string, opts?: ModelFetchOpts): Promise<void> {
    await request<void>(`/models/${encodeURIComponent(id)}`, {method: "DELETE", ...opts});
}

export async function testModel(
    id: string,
    body: Record<string, unknown>,
    opts?: ModelFetchOpts,
): Promise<TestModelResponse> {
    return request<TestModelResponse>(`/models/${encodeURIComponent(id)}/test`, {
        method: "POST",
        body,
        ...opts,
    });
}
