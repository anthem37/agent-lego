import {ApiError, type ApiErrorPayload, type ApiResponse} from "@/lib/api/types";

/** 未显式指定 `timeoutMs` 时使用的默认超时（毫秒），防止 Tab 悬挂请求占用连接。 */
export const DEFAULT_REQUEST_TIMEOUT_MS = 60_000;

export type RequestOptions = {
    method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
    query?: Record<string, string | number | boolean | undefined | null>;
    body?: unknown;
    headers?: Record<string, string>;
    signal?: AbortSignal;
    /**
     * 超时毫秒数；与 `signal` 同时存在时，任一中止即失败。
     * 未传或 `≤0` 表示不附加超时（仅使用 `signal`），避免破坏长耗时接口。
     * 需要默认超时可传 `timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS`。
     */
    timeoutMs?: number;
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

export function resolveFetchSignal(
    userSignal: AbortSignal | undefined,
    timeoutMs: number | undefined,
): AbortSignal | undefined {
    const limit = timeoutMs === undefined || timeoutMs <= 0 ? undefined : timeoutMs;
    if (limit === undefined && !userSignal) {
        return undefined;
    }
    const timeoutSignal =
        limit === undefined
            ? undefined
            : typeof AbortSignal !== "undefined" && "timeout" in AbortSignal && typeof AbortSignal.timeout === "function"
                ? AbortSignal.timeout(limit)
                : (() => {
                    const c = new AbortController();
                    setTimeout(() => c.abort(), limit);
                    return c.signal;
                })();
    if (!userSignal) {
        return timeoutSignal;
    }
    if (!timeoutSignal) {
        return userSignal;
    }
    if (typeof AbortSignal !== "undefined" && "any" in AbortSignal && typeof AbortSignal.any === "function") {
        return AbortSignal.any([userSignal, timeoutSignal]);
    }
    const merged = new AbortController();
    const abort = () => merged.abort();
    userSignal.addEventListener("abort", abort);
    timeoutSignal.addEventListener("abort", abort);
    return merged.signal;
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
        signal: resolveFetchSignal(options?.signal, options?.timeoutMs),
        cache: "no-store",
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

