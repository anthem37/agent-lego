import {request} from "@/lib/api/request";

import type {AgentDto, RunAgentResponse} from "@/lib/agents/types";

export async function createAgent(body: Record<string, unknown>): Promise<string> {
    return request<string>("/agents", {
        method: "POST",
        body,
    });
}

export async function updateAgent(agentId: string, body: Record<string, unknown>): Promise<void> {
    await request<void>(`/agents/${encodeURIComponent(agentId)}`, {
        method: "PUT",
        body,
    });
}

export async function getAgent(agentId: string): Promise<AgentDto> {
    return request<AgentDto>(`/agents/${encodeURIComponent(agentId)}`);
}

export async function runAgent(agentId: string, body: Record<string, unknown>): Promise<RunAgentResponse> {
    return request<RunAgentResponse>(`/agents/${encodeURIComponent(agentId)}/run`, {
        method: "POST",
        body,
    });
}
