/**
 * 工具模块共享类型（与后端 ToolDto / 元数据对齐）。
 */
export type ToolTypeCode = "LOCAL" | "MCP" | "HTTP" | "WORKFLOW";

export type ToolDto = {
    id: string;
    toolType: string;
    /** 语义分类：QUERY | ACTION */
    toolCategory?: string;
    name: string;
    /** 展示名/中文名（可选） */
    displayLabel?: string;
    /** 平台侧说明（可选，给人读） */
    description?: string;
    definition?: Record<string, unknown>;
    createdAt?: string;
};

/** 与后端 ToolPageDto 对齐（GET /tools 分页） */
export type ToolPageDto = {
    items: ToolDto[];
    total: number;
    page: number;
    pageSize: number;
};

export type ToolTypeMetaDto = {
    code: string;
    label: string;
    description: string;
    supportsTestCall: boolean;
};

/** GET /tools/meta/tool-categories */
export type ToolCategoryMetaDto = {
    code: string;
    label: string;
    description: string;
};

/** 与后端 LocalBuiltinParamMetaDto 对齐 */
export type LocalBuiltinParamMetaDto = {
    name: string;
    required: boolean;
    description?: string;
    type: string;
};

/** GET/PUT /tools/meta/local-builtins/exposure — 与后端 LocalBuiltinExposureRowDto 对齐 */
export type LocalBuiltinExposureRowDto = {
    name: string;
    label?: string;
    description?: string;
    /** 是否在本机 MCP Server 的 tools/list 中对外暴露 */
    exposeMcp: boolean;
    /** 是否在管理端「创建 LOCAL 工具」等下拉中展示 */
    showInUi: boolean;
};

/** PUT /tools/meta/local-builtins/exposure — 与后端 UpdateLocalBuiltinExposureRequest 对齐 */
export type UpdateLocalBuiltinExposureRequest = {
    items: Array<{
        toolName: string;
        exposeMcp: boolean;
        showInUi: boolean;
    }>;
};

/** 与后端 LocalBuiltinToolMetaDto 对齐（LOCAL 内置工具清单） */
export type LocalBuiltinToolMetaDto = {
    name: string;
    description?: string;
    label?: string;
    usageHint?: string;
    inputParameters?: LocalBuiltinParamMetaDto[];
    /** 与 HTTP parameters 对齐的 JSON Schema（object） */
    inputSchema?: Record<string, unknown>;
    /** 出参 JSON Schema：纯文本内置多为根级 type:string；也可能为带 properties 的 object */
    outputSchema?: Record<string, unknown>;
    outputJavaType?: string;
    resultConverterClass?: string;
    outputDescription?: string;
};

/** 与后端 ToolReferencesDto 对齐 */
export type ToolReferencesDto = {
    referencingAgentCount: number;
    referencingAgentIds: string[];
    /** 知识库文档 linkedToolIds 中含该工具的数量 */
    referencingKbDocumentCount: number;
};

/** 远端 MCP tools/list 单条（与后端 RemoteMcpToolMetaDto 对齐） */
export type RemoteMcpToolMetaDto = {
    name: string;
    description?: string;
    inputSchema?: Record<string, unknown>;
};

export type BatchImportMcpToolsRequest = {
    endpoint: string;
    /** 空数组表示导入远端全部（由后端约定：不传或空列表） */
    remoteToolNames?: string[];
    namePrefix?: string;
    /** 远端工具名 → 拟创建的平台工具名（可选）；未指定的仍按前缀+清洗规则生成 */
    platformNamesByRemote?: Record<string, string>;
    /**
     * true：与已有工具重名则跳过并记入 skipped。
     * false/省略：重名记入 nameConflicts，便于改平台名后重试，不整批 409。
     */
    skipExisting?: boolean;
};

export type BatchImportMcpToolsResponse = {
    created: Array<{ id: string; name: string; remoteToolName: string }>;
    skipped: Array<{ name: string; reason: string }>;
    nameConflicts?: Array<{ remoteToolName: string; attemptedPlatformName: string; reason: string }>;
};

/** HTTP 请求头一行（表单项，非 JSON） */
export type HttpHeaderRow = {
    headerName: string;
    value: string;
};

/** 与 URL 占位符 {name}、模型入参 schema 对齐的 HTTP 参数类型（含嵌套对象与数组） */
export const HTTP_PARAM_TYPES = ["string", "number", "integer", "boolean", "object", "array"] as const;
export type HttpParamType = (typeof HTTP_PARAM_TYPES)[number];

/** 数组元素为基本类型时使用；为 object 时用 arrayItemProperties 描述 items.properties */
export const HTTP_ARRAY_ITEM_PRIMITIVE_TYPES = ["string", "number", "integer", "boolean", "object"] as const;
export type HttpArrayItemPrimitiveType = (typeof HTTP_ARRAY_ITEM_PRIMITIVE_TYPES)[number];

/** 入参「值来源」说明（仅元数据，写入 parameters 各属性上的 x-agentlego-valueSource） */
export const PARAM_VALUE_SOURCE_TYPES = ["CONTEXT", "MODEL", "FIXED"] as const;
export type ParamValueSourceType = (typeof PARAM_VALUE_SOURCE_TYPES)[number];

/** HTTP 工具：模型可见入参（写入 definition.parameters，JSON Schema 子集） */
export type HttpParameterRow = {
    paramName: string;
    /**
     * 可选：运行时键名（发往 HTTP/MCP 等）。省略或与 paramName 相同时，模型参数名与下游一致。
     * 仅顶层入参行使用；写入 definition.parameterAliases。
     */
    paramAlias?: string;
    /**
     * 值来源说明：上下文参数 / 模型提取 / 固定值；默认「模型提取」。
     * 持久化为 JSON Schema 扩展字段 x-agentlego-valueSource，不改变运行时 HTTP/MCP 调用逻辑。
     */
    valueSource?: ParamValueSourceType;
    /**
     * 当 valueSource 为 FIXED 时填写；持久化为 x-agentlego-fixedValue（元数据说明，默认不参与执行层合并）。
     */
    fixedValue?: string;
    paramType: HttpParamType;
    required: boolean;
    paramDescription?: string;
    /** type 为 object 时的嵌套属性 */
    children?: HttpParameterRow[];
    /** type 为 array 时：元素类型；object 表示元素为对象，见 arrayItemProperties */
    arrayItemsPrimitiveType?: HttpArrayItemPrimitiveType;
    /** type 为 array 且元素为 object：对应 JSON Schema items.properties */
    arrayItemProperties?: HttpParameterRow[];
};

export type ToolCategoryCode = "QUERY" | "ACTION";

export type ToolFormValues = {
    toolType: ToolTypeCode;
    /** 语义分类，默认 ACTION */
    toolCategory?: ToolCategoryCode;
    name: string;
    /** 展示名/中文名（可选） */
    displayLabel?: string;
    /** 平台侧工具说明（可选）；与各类型的「模型描述」字段不同 */
    platformDescription?: string;
    toolDescription?: string;
    mcpEndpoint?: string;
    /** 远端 MCP 工具名，省略则与平台工具 name 一致 */
    mcpRemoteToolName?: string;
    /** MCP 入参 Schema 表格（写入 definition.parameters） */
    mcpParameterRows?: HttpParameterRow[];
    /** definition.parameters/inputSchema 为高级结构时保留不覆盖 */
    mcpParametersAdvancedPreserve?: boolean;
    /** MCP：definition.outputSchema 表格化编辑（逻辑返回结构，供模型与知识库级联） */
    mcpOutputParameterRows?: HttpParameterRow[];
    /** 为 true 时保存不覆盖 definition.outputSchema（高级结构） */
    mcpOutputSchemaAdvancedPreserve?: boolean;
    /** WORKFLOW：definition.outputSchema 表格化编辑 */
    workflowOutputParameterRows?: HttpParameterRow[];
    /** 为 true 时保存不覆盖 definition.outputSchema（高级结构） */
    workflowOutputSchemaAdvancedPreserve?: boolean;
    /** LOCAL：definition.outputSchema 表格化编辑 */
    localOutputParameterRows?: HttpParameterRow[];
    /** LOCAL：为 true 时保存不覆盖 definition.outputSchema（高级 JSON） */
    localOutputSchemaAdvancedPreserve?: boolean;
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
    /** WORKFLOW：definition.parameters 表格（入参） */
    workflowParameterRows?: HttpParameterRow[];
    /** 为 true 时保存不覆盖 definition.parameters / inputSchema（高级结构） */
    workflowParametersAdvancedPreserve?: boolean;
    /** LOCAL：可选自定义入参 Schema（覆盖模型侧可见参数说明；未配置时沿用内置实现） */
    localParameterRows?: HttpParameterRow[];
    /** 为 true 时保存不覆盖 definition.parameters / inputSchema */
    localParametersAdvancedPreserve?: boolean;
};

/** POST /tools/{id}/test-call */
export type TestToolCallApiResponse = {
    result?: unknown;
};
