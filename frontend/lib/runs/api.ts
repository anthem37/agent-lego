import {request, type RequestOptions} from "@/lib/api/request";

import type {WorkflowRunDto} from "@/lib/runs/types";

export type WorkflowRunFetchOpts = Pick<RequestOptions, "signal" | "timeoutMs">;

export async function getWorkflowRun(runId: string, opts?: WorkflowRunFetchOpts): Promise<WorkflowRunDto> {
    return request<WorkflowRunDto>(`/runs/${encodeURIComponent(runId)}`, {...opts});
}
