import {request} from "@/lib/api/request";

import type {RunWorkflowResponse, WorkflowDto} from "@/lib/workflows/types";

export async function createWorkflow(body: {
    name: string;
    definition?: Record<string, unknown>;
}): Promise<string> {
    return request<string>("/workflows", {method: "POST", body});
}

export async function getWorkflow(id: string, signal?: AbortSignal): Promise<WorkflowDto> {
    return request<WorkflowDto>(`/workflows/${encodeURIComponent(id)}`, {signal});
}

export async function runWorkflow(workflowId: string, body: { input: string }): Promise<RunWorkflowResponse> {
    return request<RunWorkflowResponse>(`/workflows/${encodeURIComponent(workflowId)}/runs`, {
        method: "POST",
        body,
    });
}
