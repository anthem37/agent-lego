import {request, type RequestOptions} from "@/lib/api/request";

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

export type KbFetchOpts = Pick<RequestOptions, "signal" | "timeoutMs">;

export async function listKbChunkStrategies(opts?: KbFetchOpts): Promise<KbChunkStrategyMetaDto[]> {
    const data = await request<KbChunkStrategyMetaDto[]>("/kb/meta/chunk-strategies", {...opts});
    return Array.isArray(data) ? data : [];
}

/** 智能体 knowledge_base_policy 中的 collectionIds，用于召回调试一键对齐 */
export async function listAgentKbPolicySummaries(opts?: KbFetchOpts): Promise<KbAgentPolicySummaryDto[]> {
    const data = await request<KbAgentPolicySummaryDto[]>("/kb/meta/agent-policy-summaries", {...opts});
    return Array.isArray(data) ? data : [];
}

export async function listKbCollections(opts?: KbFetchOpts): Promise<KbCollectionDto[]> {
    const data = await request<KbCollectionDto[]>("/kb/collections", {...opts});
    return Array.isArray(data) ? data : [];
}

export async function listKbDocuments(collectionId: string, opts?: KbFetchOpts): Promise<KbDocumentDto[]> {
    const data = await request<KbDocumentDto[]>(`/kb/collections/${collectionId}/documents`, {...opts});
    return Array.isArray(data) ? data : [];
}

export async function getKbDocument(
    collectionId: string,
    documentId: string,
    opts?: KbFetchOpts,
): Promise<KbDocumentDto> {
    return request<KbDocumentDto>(`/kb/collections/${collectionId}/documents/${documentId}`, {...opts});
}

export async function createKbCollection(body: CreateKbCollectionBody, opts?: KbFetchOpts): Promise<KbCollectionDto> {
    return request<KbCollectionDto>("/kb/collections", {
        method: "POST",
        body,
        ...opts,
    });
}

export async function ingestKbDocument(
    collectionId: string,
    body: IngestKbDocumentBody,
    opts?: KbFetchOpts,
): Promise<KbDocumentDto> {
    return request<KbDocumentDto>(`/kb/collections/${collectionId}/documents`, {
        method: "POST",
        body,
        ...opts,
    });
}

export async function updateKbDocument(
    collectionId: string,
    documentId: string,
    body: IngestKbDocumentBody,
    opts?: KbFetchOpts,
): Promise<KbDocumentDto> {
    return request<KbDocumentDto>(`/kb/collections/${collectionId}/documents/${documentId}`, {
        method: "PUT",
        body,
        ...opts,
    });
}

export async function deleteKbCollection(
    collectionId: string,
    opts?: KbFetchOpts,
): Promise<KbCollectionDeleteResult> {
    return request<KbCollectionDeleteResult>(`/kb/collections/${collectionId}`, {
        method: "DELETE",
        ...opts,
    });
}

export async function deleteKbDocument(collectionId: string, documentId: string, opts?: KbFetchOpts): Promise<void> {
    await request<null>(`/kb/collections/${collectionId}/documents/${documentId}`, {
        method: "DELETE",
        ...opts,
    });
}

export async function validateKbDocument(
    collectionId: string,
    documentId: string,
    opts?: KbFetchOpts,
): Promise<KbDocumentValidationResponse> {
    return request<KbDocumentValidationResponse>(
        `/kb/collections/${collectionId}/documents/${documentId}/validate`,
        {method: "POST", ...opts},
    );
}

export async function previewKbRetrieve(
    collectionId: string,
    body: KbRetrievePreviewRequest,
    opts?: KbFetchOpts,
): Promise<KbRetrievePreviewResponse> {
    return request<KbRetrievePreviewResponse>(`/kb/collections/${collectionId}/retrieve-preview`, {
        method: "POST",
        body,
        ...opts,
    });
}

/** 多集合联合召回（须相同 embedding 模型），对齐智能体 knowledge_base_policy 多集合 */
export async function previewKbRetrieveMulti(
    body: KbMultiRetrievePreviewRequest,
    opts?: KbFetchOpts,
): Promise<KbRetrievePreviewResponse> {
    return request<KbRetrievePreviewResponse>("/kb/retrieve-preview", {
        method: "POST",
        body,
        ...opts,
    });
}

/** 校验某集合下全部文档 */
export async function validateKbCollectionDocuments(
    collectionId: string,
    body?: KbValidateCollectionDocumentsRequest,
    opts?: KbFetchOpts,
): Promise<KbCollectionDocumentsValidationResponse> {
    return request<KbCollectionDocumentsValidationResponse>(
        `/kb/collections/${collectionId}/documents/validate-all`,
        {method: "POST", body: body ?? {}, ...opts},
    );
}

export async function renderKbDocument(
    collectionId: string,
    documentId: string,
    body?: KbRenderDocumentBody,
    opts?: KbFetchOpts,
): Promise<KbRenderDocumentResponse> {
    return request<KbRenderDocumentResponse>(
        `/kb/collections/${collectionId}/documents/${documentId}/render`,
        {method: "POST", body: body ?? {}, ...opts},
    );
}
