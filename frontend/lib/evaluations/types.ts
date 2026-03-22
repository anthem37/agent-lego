export type EvalCaseDto = { input: string; expectedOutput: string };

export type RunEvaluationResponse = { runId: string; status: string };

/** GET /evaluations/runs/{id} */
export type RunEvaluationDto = {
    id: string;
    evaluationId: string;
    status: string;
    input?: Record<string, unknown>;
    metrics?: Record<string, unknown>;
    trace?: Record<string, unknown>;
    error?: string;
    startedAt?: string;
    finishedAt?: string;
    createdAt?: string;
};
