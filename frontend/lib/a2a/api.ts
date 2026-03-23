import {request, type RequestOptions} from "@/lib/api/request";

export type A2aFetchOpts = Pick<RequestOptions, "signal" | "timeoutMs">;

/** 委派可能长时间阻塞；默认不设 `timeoutMs`，由调用方传入或传 `signal` 中止。 */
export async function delegateA2a(body: Record<string, unknown>, opts?: A2aFetchOpts): Promise<string> {
    return request<string>("/a2a/delegate", {method: "POST", body, ...opts});
}
