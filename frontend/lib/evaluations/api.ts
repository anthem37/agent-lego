import {request, type RequestOptions} from "@/lib/api/request";

import type {RunEvaluationDto, RunEvaluationResponse} from "@/lib/evaluations/types";

export type EvaluationRunFetchOpts = Pick<RequestOptions, "signal" | "timeoutMs">;

export async function createEvaluation(
    body: {
        name: string;
        agentId: string;
        modelId: string;
        cases: { input: string; expectedOutput: string }[];
        /** 可选：与智能体 run 的 memoryNamespace 一致 */
        memoryNamespace?: string;
    },
    opts?: EvaluationRunFetchOpts,
): Promise<string> {
    return request<string>("/evaluations", {method: "POST", body, ...opts});
}

/** 触发运行可能异步排队或较长；默认不设 `timeoutMs`，由调用方传入或传 `signal` 中止。 */
export async function triggerEvaluationRun(
    evaluationId: string,
    opts?: EvaluationRunFetchOpts,
): Promise<RunEvaluationResponse> {
    return request<RunEvaluationResponse>(`/evaluations/${encodeURIComponent(evaluationId)}/runs`, {
        method: "POST",
        ...opts,
    });
}

export async function getEvaluationRun(runId: string, opts?: EvaluationRunFetchOpts): Promise<RunEvaluationDto> {
    return request<RunEvaluationDto>(`/evaluations/runs/${encodeURIComponent(runId)}`, {...opts});
}
