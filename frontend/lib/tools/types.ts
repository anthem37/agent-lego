/**
 * 工具模块共享类型（与后端 ToolDto / 元数据对齐）。
 */
export type ToolTypeCode = "LOCAL" | "MCP" | "HTTP" | "WORKFLOW";

export type ToolDto = {
    id: string;
    toolType: string;
    name: string;
    definition?: Record<string, unknown>;
    createdAt?: string;
};

export type ToolTypeMetaDto = {
    code: string;
    label: string;
    description: string;
    supportsTestCall: boolean;
};

/** 与后端 LocalBuiltinParamMetaDto 对齐 */
export type LocalBuiltinParamMetaDto = {
    name: string;
    required: boolean;
    description?: string;
    type: string;
};

/** 与后端 LocalBuiltinToolMetaDto 对齐（LOCAL 内置工具清单） */
export type LocalBuiltinToolMetaDto = {
    name: string;
    description?: string;
    label?: string;
    usageHint?: string;
    inputParameters?: LocalBuiltinParamMetaDto[];
    outputJavaType?: string;
    resultConverterClass?: string;
    outputDescription?: string;
};

/** 与后端 ToolReferencesDto 对齐 */
export type ToolReferencesDto = {
    referencingAgentCount: number;
    referencingAgentIds: string[];
};

/** HTTP 请求头一行（表单项，非 JSON） */
export type HttpHeaderRow = {
    headerName: string;
    value: string;
};

/** 与 URL 占位符 {name}、模型入参 schema 对齐的 HTTP 参数类型 */
export const HTTP_PARAM_TYPES = ["string", "number", "integer", "boolean"] as const;
export type HttpParamType = (typeof HTTP_PARAM_TYPES)[number];

/** HTTP 工具：模型可见入参（写入 definition.parameters，JSON Schema 子集） */
export type HttpParameterRow = {
    paramName: string;
    paramType: HttpParamType;
    required: boolean;
    paramDescription?: string;
};

export type ToolFormValues = {
    toolType: ToolTypeCode;
    name: string;
    toolDescription?: string;
    mcpEndpoint?: string;
    /** 远端 MCP 工具名，省略则与平台工具 name 一致 */
    mcpRemoteToolName?: string;
    /** MCP 入参 Schema 表格（写入 definition.parameters） */
    mcpParameterRows?: HttpParameterRow[];
    /** definition.parameters/inputSchema 为高级结构时保留不覆盖 */
    mcpParametersAdvancedPreserve?: boolean;
    httpUrl?: string;
    httpMethod?: string;
    /** 请求头：逐行填写，无需写 JSON */
    httpHeaderRows?: HttpHeaderRow[];
    /** 模型/联调入参定义（对应 definition.parameters） */
    httpParameterRows?: HttpParameterRow[];
    /**
     * 为 true 时：definition 中存在无法用当前表格安全表达的 parameters/inputSchema，
     * 保存 URL/请求头等时不会覆盖这两字段（避免误删高级 JSON Schema）。
     */
    httpParametersAdvancedPreserve?: boolean;
    /** HTTP 响应体逻辑结构 → definition.outputSchema（表格编辑） */
    httpOutputParameterRows?: HttpParameterRow[];
    /** 为 true 时保存不覆盖 definition.outputSchema（高级结构） */
    httpOutputSchemaAdvancedPreserve?: boolean;
    sendJsonBody?: boolean;
    workflowId?: string;
};
