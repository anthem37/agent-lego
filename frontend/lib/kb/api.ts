import {request} from "@/lib/api/request";

import type {
    CreateKbCollectionBody,
    IngestKbDocumentBody,
    KbAgentPolicySummaryDto,
    KbChunkStrategyMetaDto,
    KbCollectionDeleteResult,
    KbCollectionDocumentsValidationResponse,
    KbCollectionDto,
    KbDocumentDto,
    KbDocumentValidationResponse,
    KbMultiRetrievePreviewRequest,
    KbRenderDocumentBody,
    KbRenderDocumentResponse,
    KbRetrievePreviewRequest,
    KbRetrievePreviewResponse,
    KbValidateCollectionDocumentsRequest,
} from "@/lib/kb/types";

export async function listKbChunkStrategies(signal?: AbortSignal): Promise<KbChunkStrategyMetaDto[]> {
    const data = await request<KbChunkStrategyMetaDto[]>("/kb/meta/chunk-strategies", {signal});
    return Array.isArray(data) ? data : [];
}

/** 智能体 knowledge_base_policy 中的 collectionIds，用于召回调试一键对齐 */
export async function listAgentKbPolicySummaries(signal?: AbortSignal): Promise<KbAgentPolicySummaryDto[]> {
    const data = await request<KbAgentPolicySummaryDto[]>("/kb/meta/agent-policy-summaries", {signal});
    return Array.isArray(data) ? data : [];
}

export async function listKbCollections(signal?: AbortSignal): Promise<KbCollectionDto[]> {
    const data = await request<KbCollectionDto[]>("/kb/collections", {signal});
    return Array.isArray(data) ? data : [];
}

export async function listKbDocuments(collectionId: string, signal?: AbortSignal): Promise<KbDocumentDto[]> {
    const data = await request<KbDocumentDto[]>(`/kb/collections/${collectionId}/documents`, {signal});
    return Array.isArray(data) ? data : [];
}

export async function getKbDocument(
    collectionId: string,
    documentId: string,
    signal?: AbortSignal,
): Promise<KbDocumentDto> {
    return request<KbDocumentDto>(`/kb/collections/${collectionId}/documents/${documentId}`, {signal});
}

export async function createKbCollection(
    body: CreateKbCollectionBody,
    signal?: AbortSignal,
): Promise<KbCollectionDto> {
    return request<KbCollectionDto>("/kb/collections", {
        method: "POST",
        body,
        signal,
    });
}

export async function ingestKbDocument(
    collectionId: string,
    body: IngestKbDocumentBody,
    signal?: AbortSignal,
): Promise<KbDocumentDto> {
    return request<KbDocumentDto>(`/kb/collections/${collectionId}/documents`, {
        method: "POST",
        body,
        signal,
    });
}

export async function updateKbDocument(
    collectionId: string,
    documentId: string,
    body: IngestKbDocumentBody,
    signal?: AbortSignal,
): Promise<KbDocumentDto> {
    return request<KbDocumentDto>(`/kb/collections/${collectionId}/documents/${documentId}`, {
        method: "PUT",
        body,
        signal,
    });
}

export async function deleteKbCollection(
    collectionId: string,
    signal?: AbortSignal,
): Promise<KbCollectionDeleteResult> {
    return request<KbCollectionDeleteResult>(`/kb/collections/${collectionId}`, {
        method: "DELETE",
        signal,
    });
}

export async function deleteKbDocument(
    collectionId: string,
    documentId: string,
    signal?: AbortSignal,
): Promise<void> {
    await request<null>(`/kb/collections/${collectionId}/documents/${documentId}`, {
        method: "DELETE",
        signal,
    });
}

export async function validateKbDocument(
    collectionId: string,
    documentId: string,
    signal?: AbortSignal,
): Promise<KbDocumentValidationResponse> {
    return request<KbDocumentValidationResponse>(
        `/kb/collections/${collectionId}/documents/${documentId}/validate`,
        {method: "POST", signal},
    );
}

export async function previewKbRetrieve(
    collectionId: string,
    body: KbRetrievePreviewRequest,
    signal?: AbortSignal,
): Promise<KbRetrievePreviewResponse> {
    return request<KbRetrievePreviewResponse>(`/kb/collections/${collectionId}/retrieve-preview`, {
        method: "POST",
        body,
        signal,
    });
}

/** 多集合联合召回（须相同 embedding 模型），对齐智能体 knowledge_base_policy 多集合 */
export async function previewKbRetrieveMulti(
    body: KbMultiRetrievePreviewRequest,
    signal?: AbortSignal,
): Promise<KbRetrievePreviewResponse> {
    return request<KbRetrievePreviewResponse>("/kb/retrieve-preview", {
        method: "POST",
        body,
        signal,
    });
}

/** 校验某集合下全部文档 */
export async function validateKbCollectionDocuments(
    collectionId: string,
    body?: KbValidateCollectionDocumentsRequest,
    signal?: AbortSignal,
): Promise<KbCollectionDocumentsValidationResponse> {
    return request<KbCollectionDocumentsValidationResponse>(
        `/kb/collections/${collectionId}/documents/validate-all`,
        {method: "POST", body: body ?? {}, signal},
    );
}

export async function renderKbDocument(
    collectionId: string,
    documentId: string,
    body?: KbRenderDocumentBody,
    signal?: AbortSignal,
): Promise<KbRenderDocumentResponse> {
    return request<KbRenderDocumentResponse>(
        `/kb/collections/${collectionId}/documents/${documentId}/render`,
        {method: "POST", body: body ?? {}, signal},
    );
}
