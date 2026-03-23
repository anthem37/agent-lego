import {request, type RequestOptions} from "@/lib/api/request";

import type {RunWorkflowResponse, WorkflowDto} from "@/lib/workflows/types";

export type WorkflowFetchOpts = Pick<RequestOptions, "signal" | "timeoutMs">;

export async function createWorkflow(
    body: {
        name: string;
        definition?: Record<string, unknown>;
    },
    opts?: WorkflowFetchOpts,
): Promise<string> {
    return request<string>("/workflows", {method: "POST", body, ...opts});
}

export async function getWorkflow(id: string, opts?: WorkflowFetchOpts): Promise<WorkflowDto> {
    return request<WorkflowDto>(`/workflows/${encodeURIComponent(id)}`, {...opts});
}

/** 运行可能长时间阻塞；默认不设 `timeoutMs`，由调用方传入或传 `signal` 中止。 */
export async function runWorkflow(
    workflowId: string,
    body: { input: string },
    opts?: WorkflowFetchOpts,
): Promise<RunWorkflowResponse> {
    return request<RunWorkflowResponse>(`/workflows/${encodeURIComponent(workflowId)}/runs`, {
        method: "POST",
        body,
        ...opts,
    });
}
