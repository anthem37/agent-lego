import {request} from "@/lib/api/request";

import type {WorkflowRunDto} from "@/lib/runs/types";

export async function getWorkflowRun(runId: string, signal?: AbortSignal): Promise<WorkflowRunDto> {
    return request<WorkflowRunDto>(`/runs/${encodeURIComponent(runId)}`, {signal});
}
