import {request, type RequestOptions} from "@/lib/api/request";

import type {
    BatchImportMcpToolsRequest,
    BatchImportMcpToolsResponse,
    LocalBuiltinExposureRowDto,
    LocalBuiltinToolMetaDto,
    RemoteMcpToolMetaDto,
    TestToolCallApiResponse,
    ToolCategoryMetaDto,
    ToolDto,
    ToolPageDto,
    ToolReferencesDto,
    ToolTypeMetaDto,
    UpdateLocalBuiltinExposureRequest,
} from "@/lib/tools/types";

export type ToolFetchOpts = Pick<RequestOptions, "signal" | "timeoutMs">;

export type ListToolsPageParams = {
    page?: number;
    pageSize?: number;
    /** 服务端模糊匹配：名称、ID、类型、definition 文本 */
    q?: string;
    /** 按工具类型精确筛选（如 LOCAL、MCP），与后端枚举一致 */
    toolType?: string;
};

export async function listToolsPage(params?: ListToolsPageParams, opts?: ToolFetchOpts): Promise<ToolPageDto> {
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
        ...opts,
    });
    return {
        items: Array.isArray(data.items) ? data.items : [],
        total: typeof data.total === "number" ? data.total : 0,
        page: typeof data.page === "number" ? data.page : page,
        pageSize: typeof data.pageSize === "number" ? data.pageSize : pageSize,
    };
}

export async function getTool(id: string, opts?: ToolFetchOpts): Promise<ToolDto> {
    return request<ToolDto>(`/tools/${id}`, {...opts});
}

export async function fetchToolCategoryMeta(opts?: ToolFetchOpts): Promise<ToolCategoryMetaDto[]> {
    const data = await request<ToolCategoryMetaDto[]>("/tools/meta/tool-categories", {...opts});
    return Array.isArray(data) ? data : [];
}

export async function createTool(
    body: {
        toolType: string;
        name: string;
        toolCategory?: string;
        displayLabel?: string;
        description?: string;
        definition?: Record<string, unknown>;
    },
    opts?: ToolFetchOpts,
): Promise<string> {
    return request<string>("/tools", {method: "POST", body, ...opts});
}

export async function updateTool(
    id: string,
    body: {
        toolType: string;
        name: string;
        toolCategory?: string;
        displayLabel?: string;
        description?: string;
        definition?: Record<string, unknown>;
    },
    opts?: ToolFetchOpts,
): Promise<void> {
    await request<unknown>(`/tools/${id}`, {method: "PUT", body, ...opts});
}

export async function deleteTool(id: string, opts?: ToolFetchOpts): Promise<void> {
    await request<unknown>(`/tools/${id}`, {method: "DELETE", ...opts});
}

export async function fetchToolReferences(id: string, opts?: ToolFetchOpts): Promise<ToolReferencesDto> {
    return request<ToolReferencesDto>(`/tools/${encodeURIComponent(id)}/references`, {...opts});
}

export async function fetchToolTypeMeta(opts?: ToolFetchOpts): Promise<ToolTypeMetaDto[]> {
    const data = await request<ToolTypeMetaDto[]>("/tools/meta/tool-types", {...opts});
    return Array.isArray(data) ? data : [];
}

export async function fetchLocalBuiltinToolsMeta(opts?: ToolFetchOpts): Promise<LocalBuiltinToolMetaDto[]> {
    const data = await request<LocalBuiltinToolMetaDto[]>("/tools/meta/local-builtins", {...opts});
    return Array.isArray(data) ? data : [];
}

/** 已注册内置 + MCP/管理端暴露开关（与 lego_local_builtin_tool_exposure 对齐） */
export async function fetchLocalBuiltinExposureSettings(
    opts?: ToolFetchOpts,
): Promise<LocalBuiltinExposureRowDto[]> {
    const data = await request<LocalBuiltinExposureRowDto[]>("/tools/meta/local-builtins/exposure", {...opts});
    return Array.isArray(data) ? data : [];
}

export async function updateLocalBuiltinExposure(
    body: UpdateLocalBuiltinExposureRequest,
    opts?: ToolFetchOpts,
): Promise<void> {
    await request<unknown>("/tools/meta/local-builtins/exposure", {method: "PUT", body, ...opts});
}

/** 连接外部 MCP 并拉取 tools/list；refresh=true 时忽略缓存 */
export async function fetchRemoteMcpTools(
    endpoint: string,
    refresh = false,
    opts?: ToolFetchOpts,
): Promise<RemoteMcpToolMetaDto[]> {
    const q = new URLSearchParams();
    q.set("endpoint", endpoint);
    if (refresh) {
        q.set("refresh", "true");
    }
    const data = await request<RemoteMcpToolMetaDto[]>(`/tools/meta/mcp/remote-tools?${q.toString()}`, {...opts});
    return Array.isArray(data) ? data : [];
}

export async function batchImportMcpTools(
    body: BatchImportMcpToolsRequest,
    opts?: ToolFetchOpts,
): Promise<BatchImportMcpToolsResponse> {
    return request<BatchImportMcpToolsResponse>("/tools/meta/mcp/batch-import", {method: "POST", body, ...opts});
}

export async function testToolCall(
    toolId: string,
    body: { input: Record<string, unknown> },
    opts?: ToolFetchOpts,
): Promise<TestToolCallApiResponse> {
    return request<TestToolCallApiResponse>(`/tools/${encodeURIComponent(toolId)}/test-call`, {
        method: "POST",
        body,
        ...opts,
    });
}
