export type ApiResponse<T> = {
    code: string;
    message: string;
    data: T;
    traceId: string;
};

export type ApiErrorPayload = {
    code?: string;
    message?: string;
    traceId?: string;
};

export class ApiError extends Error {
    public readonly code?: string;
    public readonly traceId?: string;
    public readonly httpStatus?: number;

    public constructor(message: string, options?: { code?: string; traceId?: string; httpStatus?: number }) {
        super(message);
        this.name = "ApiError";
        this.code = options?.code;
        this.traceId = options?.traceId;
        this.httpStatus = options?.httpStatus;
    }
}

