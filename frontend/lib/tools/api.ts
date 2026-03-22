import {request} from "@/lib/api/request";

import type {
    BatchImportMcpToolsRequest,
    BatchImportMcpToolsResponse,
    LocalBuiltinToolMetaDto,
    RemoteMcpToolMetaDto,
    TestToolCallApiResponse,
    ToolCategoryMetaDto,
    ToolDto,
    ToolPageDto,
    ToolReferencesDto,
    ToolTypeMetaDto,
} from "@/lib/tools/types";

export type ListToolsPageParams = {
    page?: number;
    pageSize?: number;
    /** 服务端模糊匹配：名称、ID、类型、definition 文本 */
    q?: string;
    /** 按工具类型精确筛选（如 LOCAL、MCP），与后端枚举一致 */
    toolType?: string;
};

export async function listToolsPage(params?: ListToolsPageParams): Promise<ToolPageDto> {
    const page = params?.page ?? 1;
    const pageSize = params?.pageSize ?? 50;
    const q = params?.q?.trim();
    const toolType = params?.toolType?.trim();
    const data = await request<ToolPageDto>("/tools", {
        query: {
            page,
            pageSize,
            ...(q ? {q} : {}),
            ...(toolType ? {toolType} : {}),
        },
    });
    return {
        items: Array.isArray(data.items) ? data.items : [],
        total: typeof data.total === "number" ? data.total : 0,
        page: typeof data.page === "number" ? data.page : page,
        pageSize: typeof data.pageSize === "number" ? data.pageSize : pageSize,
    };
}

export async function getTool(id: string): Promise<ToolDto> {
    return request<ToolDto>(`/tools/${id}`);
}

export async function fetchToolCategoryMeta(): Promise<ToolCategoryMetaDto[]> {
    const data = await request<ToolCategoryMetaDto[]>("/tools/meta/tool-categories");
    return Array.isArray(data) ? data : [];
}

export async function createTool(body: {
    toolType: string;
    name: string;
    toolCategory?: string;
    displayLabel?: string;
    description?: string;
    definition?: Record<string, unknown>
}): Promise<string> {
    return request<string>("/tools", {method: "POST", body});
}

export async function updateTool(
    id: string,
    body: {
        toolType: string;
        name: string;
        toolCategory?: string;
        displayLabel?: string;
        description?: string;
        definition?: Record<string, unknown>
    },
): Promise<void> {
    await request<unknown>(`/tools/${id}`, {method: "PUT", body});
}

export async function deleteTool(id: string): Promise<void> {
    await request<unknown>(`/tools/${id}`, {method: "DELETE"});
}

export async function fetchToolReferences(id: string): Promise<ToolReferencesDto> {
    return request<ToolReferencesDto>(`/tools/${encodeURIComponent(id)}/references`);
}

export async function fetchToolTypeMeta(): Promise<ToolTypeMetaDto[]> {
    const data = await request<ToolTypeMetaDto[]>("/tools/meta/tool-types");
    return Array.isArray(data) ? data : [];
}

export async function fetchLocalBuiltinToolsMeta(): Promise<LocalBuiltinToolMetaDto[]> {
    const data = await request<LocalBuiltinToolMetaDto[]>("/tools/meta/local-builtins");
    return Array.isArray(data) ? data : [];
}

/** 连接外部 MCP 并拉取 tools/list；refresh=true 时忽略缓存 */
export async function fetchRemoteMcpTools(endpoint: string, refresh = false): Promise<RemoteMcpToolMetaDto[]> {
    const q = new URLSearchParams();
    q.set("endpoint", endpoint);
    if (refresh) {
        q.set("refresh", "true");
    }
    const data = await request<RemoteMcpToolMetaDto[]>(`/tools/meta/mcp/remote-tools?${q.toString()}`);
    return Array.isArray(data) ? data : [];
}

export async function batchImportMcpTools(
    body: BatchImportMcpToolsRequest,
): Promise<BatchImportMcpToolsResponse> {
    return request<BatchImportMcpToolsResponse>("/tools/meta/mcp/batch-import", {method: "POST", body});
}

export async function testToolCall(
    toolId: string,
    body: { input: Record<string, unknown> },
): Promise<TestToolCallApiResponse> {
    return request<TestToolCallApiResponse>(`/tools/${encodeURIComponent(toolId)}/test-call`, {
        method: "POST",
        body,
    });
}
