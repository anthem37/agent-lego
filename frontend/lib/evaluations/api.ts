import {request} from "@/lib/api/request";

import type {RunEvaluationDto, RunEvaluationResponse} from "@/lib/evaluations/types";

export async function createEvaluation(body: {
    name: string;
    agentId: string;
    modelId: string;
    cases: { input: string; expectedOutput: string }[];
}): Promise<string> {
    return request<string>("/evaluations", {method: "POST", body});
}

export async function triggerEvaluationRun(evaluationId: string): Promise<RunEvaluationResponse> {
    return request<RunEvaluationResponse>(`/evaluations/${encodeURIComponent(evaluationId)}/runs`, {
        method: "POST",
    });
}

export async function getEvaluationRun(runId: string, signal?: AbortSignal): Promise<RunEvaluationDto> {
    return request<RunEvaluationDto>(`/evaluations/runs/${encodeURIComponent(runId)}`, {signal});
}
