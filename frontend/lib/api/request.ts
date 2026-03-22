import {ApiError, type ApiErrorPayload, type ApiResponse} from "@/lib/api/types";

export type RequestOptions = {
    method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
    query?: Record<string, string | number | boolean | undefined | null>;
    body?: unknown;
    headers?: Record<string, string>;
    signal?: AbortSignal;
};

function buildUrl(path: string, query?: RequestOptions["query"]): string {
    const normalized = path.startsWith("/") ? path : `/${path}`;
    const url = new URL(`/api${normalized}`, "http://localhost");
    if (query) {
        Object.entries(query).forEach(([k, v]) => {
            if (v === undefined || v === null) {
                return;
            }
            url.searchParams.set(k, String(v));
        });
    }
    return `${url.pathname}${url.search}`;
}

async function tryParseJson<T>(res: Response): Promise<T | null> {
    const text = await res.text();
    if (!text) {
        return null;
    }
    try {
        return JSON.parse(text) as T;
    } catch {
        return null;
    }
}

export async function request<T>(path: string, options?: RequestOptions): Promise<T> {
    const method = options?.method ?? "GET";
    const url = buildUrl(path, options?.query);
    const headers: Record<string, string> = {
        ...(options?.headers ?? {}),
    };

    let body: BodyInit | undefined;
    if (options?.body !== undefined) {
        headers["content-type"] = headers["content-type"] ?? "application/json";
        body = JSON.stringify(options.body);
    }

    const res = await fetch(url, {
        method,
        headers,
        body,
        signal: options?.signal,
    });

    // 先尝试解析后端统一结构（即使非 2xx，也尽量把 code/message/traceId 提取出来）
    const parsed = await tryParseJson<ApiResponse<T> | ApiErrorPayload>(res);

    if (!res.ok) {
        const payload = (parsed ?? {}) as ApiErrorPayload;
        const message = payload.message ?? `HTTP ${res.status}`;
        throw new ApiError(message, {
            code: payload.code,
            traceId: payload.traceId,
            httpStatus: res.status,
        });
    }

    if (res.status === 204) {
        return undefined as T;
    }

    const apiResp = parsed as ApiResponse<T> | null;
    if (!apiResp || typeof apiResp !== "object") {
        throw new ApiError("响应解析失败（不是有效 JSON）");
    }

    if (apiResp.code !== "OK") {
        throw new ApiError(apiResp.message ?? "请求失败", {code: apiResp.code, traceId: apiResp.traceId});
    }

    return apiResp.data;
}

