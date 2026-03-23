import {request, type RequestOptions} from "@/lib/api/request";

import type {AgentDto, RunAgentResponse} from "@/lib/agents/types";

export type AgentFetchOpts = Pick<RequestOptions, "signal" | "timeoutMs">;

export async function createAgent(body: Record<string, unknown>, opts?: AgentFetchOpts): Promise<string> {
    return request<string>("/agents", {
        method: "POST",
        body,
        ...opts,
    });
}

export async function updateAgent(
    agentId: string,
    body: Record<string, unknown>,
    opts?: AgentFetchOpts,
): Promise<void> {
    await request<void>(`/agents/${encodeURIComponent(agentId)}`, {
        method: "PUT",
        body,
        ...opts,
    });
}

export async function getAgent(agentId: string, opts?: AgentFetchOpts): Promise<AgentDto> {
    return request<AgentDto>(`/agents/${encodeURIComponent(agentId)}`, opts);
}

/**
 * 试运行可能长时间阻塞，默认不设 `timeoutMs`；需要上限时由调用方传入（或传 `signal` 在卸载时中止）。
 */
export async function runAgent(
    agentId: string,
    body: Record<string, unknown>,
    opts?: AgentFetchOpts,
): Promise<RunAgentResponse> {
    return request<RunAgentResponse>(`/agents/${encodeURIComponent(agentId)}/run`, {
        method: "POST",
        body,
        ...opts,
    });
}
