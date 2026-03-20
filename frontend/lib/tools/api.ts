import {request} from "@/lib/api/request";

import type {LocalBuiltinToolMetaDto, ToolDto, ToolReferencesDto, ToolTypeMetaDto} from "@/lib/tools/types";

export async function listTools(): Promise<ToolDto[]> {
    const list = await request<ToolDto[]>("/tools");
    return Array.isArray(list) ? list : [];
}

export async function getTool(id: string): Promise<ToolDto> {
    return request<ToolDto>(`/tools/${id}`);
}

export async function createTool(body: {
    toolType: string;
    name: string;
    definition?: Record<string, unknown>
}): Promise<string> {
    return request<string>("/tools", {method: "POST", body});
}

export async function updateTool(
    id: string,
    body: { toolType: string; name: string; definition?: Record<string, unknown> },
): Promise<void> {
    await request<unknown>(`/tools/${id}`, {method: "PUT", body});
}

export async function deleteTool(id: string): Promise<void> {
    await request<unknown>(`/tools/${id}`, {method: "DELETE"});
}

export async function fetchToolReferences(id: string): Promise<ToolReferencesDto> {
    return request(`/tools/${id}/references`);
}

export async function fetchToolTypeMeta(): Promise<ToolTypeMetaDto[]> {
    const data = await request<ToolTypeMetaDto[]>("/tools/meta/tool-types");
    return Array.isArray(data) ? data : [];
}

export async function fetchLocalBuiltinToolsMeta(): Promise<LocalBuiltinToolMetaDto[]> {
    const data = await request<LocalBuiltinToolMetaDto[]>("/tools/meta/local-builtins");
    return Array.isArray(data) ? data : [];
}
