/** GET /runs/{id} */
export type WorkflowRunDto = {
    id: string;
    workflowId: string;
    status: string;
    input?: Record<string, unknown>;
    output?: Record<string, unknown>;
    error?: string;
    startedAt?: string;
    finishedAt?: string;
    createdAt?: string;
};
