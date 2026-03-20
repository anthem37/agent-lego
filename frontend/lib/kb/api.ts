import {request} from "@/lib/api/request";

import type {
    CreateKbBaseBody,
    CreateKnowledgeBody,
    KbBaseDto,
    KbDocumentPageDto,
    KbIngestResponse,
    KbKnowledgeDetailDto,
    KbQueryBody,
    KbQueryResponse,
    UpdateKbBaseBody,
} from "@/lib/kb/types";

/** 列出全部知识库（含统计） */
export async function listKbBases(): Promise<KbBaseDto[]> {
    const data = await request<KbBaseDto[]>("/kb/bases");
    return Array.isArray(data) ? data : [];
}

export async function createKbBase(body: CreateKbBaseBody): Promise<KbBaseDto> {
    return request<KbBaseDto>("/kb/bases", {method: "POST", body});
}

export async function updateKbBase(id: string, body: UpdateKbBaseBody): Promise<KbBaseDto> {
    return request<KbBaseDto>(`/kb/bases/${id}`, {method: "PUT", body});
}

export async function deleteKbBase(id: string): Promise<void> {
    await request<unknown>(`/kb/bases/${id}`, {method: "DELETE"});
}

/** 某知识库下的知识（文档）分页 */
export async function listKnowledge(params: {
    baseId: string;
    page?: number;
    pageSize?: number;
}): Promise<KbDocumentPageDto> {
    const page = params.page ?? 1;
    const pageSize = params.pageSize ?? 20;
    return request<KbDocumentPageDto>(`/kb/bases/${params.baseId}/knowledge`, {
        query: {page, pageSize},
    });
}

export async function addKnowledge(baseId: string, body: CreateKnowledgeBody): Promise<KbIngestResponse> {
    return request<KbIngestResponse>(`/kb/bases/${baseId}/knowledge`, {method: "POST", body});
}

export async function getKnowledgeDocument(documentId: string): Promise<KbKnowledgeDetailDto> {
    return request<KbKnowledgeDetailDto>(`/kb/knowledge/${documentId}`);
}

export async function deleteKnowledgeDocument(documentId: string): Promise<void> {
    await request<unknown>(`/kb/knowledge/${documentId}`, {method: "DELETE"});
}

export async function queryKb(body: KbQueryBody): Promise<KbQueryResponse> {
    const data = await request<KbQueryResponse>("/kb/query", {method: "POST", body});
    return {chunks: Array.isArray(data.chunks) ? data.chunks : []};
}
