export type WorkflowDto = {
    id: string;
    name: string;
    definition?: Record<string, unknown>;
    createdAt?: string;
};

export type RunWorkflowForm = {
    input: string;
};

export type RunWorkflowResponse = {
    runId: string;
    status: string;
};
