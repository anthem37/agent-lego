import {request} from "@/lib/api/request";

import type {
    CreateKbCollectionBody,
    IngestKbDocumentBody,
    KbChunkStrategyMetaDto,
    KbCollectionDeleteResult,
    KbCollectionDto,
    KbDocumentDto,
    RenderKbDocumentBody,
    RenderKbDocumentResult,
} from "@/lib/kb/types";

export async function listKbChunkStrategies(signal?: AbortSignal): Promise<KbChunkStrategyMetaDto[]> {
    const data = await request<KbChunkStrategyMetaDto[]>("/kb/meta/chunk-strategies", {signal});
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

export async function renderKbDocumentBody(
    collectionId: string,
    documentId: string,
    body: RenderKbDocumentBody,
    signal?: AbortSignal,
): Promise<RenderKbDocumentResult> {
    return request<RenderKbDocumentResult>(
        `/kb/collections/${collectionId}/documents/${documentId}/render`,
        {method: "POST", body, signal},
    );
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
