import type {
    HttpHeaderRow,
    HttpParameterRow,
    HttpParamType,
    ToolDto,
    ToolFormValues,
    ToolTypeCode,
} from "@/lib/tools/types";
import {HTTP_PARAM_TYPES} from "@/lib/tools/types";

/** HTTP 入参类型下拉 */
export const HTTP_PARAM_TYPE_OPTIONS = HTTP_PARAM_TYPES.map((t) => ({value: t, label: t}));

const HTTP_METHODS_BODY = new Set(["POST", "PUT", "PATCH"]);

const HTTP_OWNED_BASE = new Set([
    "url",
    "method",
    "headers",
    "description",
    "sendJsonBody",
]);
const HTTP_PARAM_SCHEMA_KEYS = new Set(["parameters", "inputSchema"]);
const HTTP_OUTPUT_SCHEMA_KEYS = new Set(["outputSchema"]);

function buildHttpOwnedKeys(preserveParams: boolean, preserveOutput: boolean): Set<string> {
    const s = new Set(HTTP_OWNED_BASE);
    if (!preserveParams) {
        for (const k of HTTP_PARAM_SCHEMA_KEYS) {
            s.add(k);
        }
    }
    if (!preserveOutput) {
        for (const k of HTTP_OUTPUT_SCHEMA_KEYS) {
            s.add(k);
        }
    }
    return s;
}

const WORKFLOW_OWNED_KEYS = new Set(["workflowId", "description"]);

function buildMcpOwnedKeys(preserveParams: boolean): Set<string> {
    const s = new Set<string>(["description", "endpoint", "mcpToolName"]);
    if (!preserveParams) {
        s.add("parameters");
        s.add("inputSchema");
    }
    return s;
}

const LOCAL_OWNED_KEYS = new Set(["description"]);

export type BuildDefinitionOptions = {
    /** 编辑时传入：表单未展示的 definition 键会原样保留 */
    existingDefinition?: Record<string, unknown> | undefined;
};

/** 与 HttpProxyAgentTool 默认宽松入参一致时可当「空表单」编辑 */
function isDefaultLooseBackendParameterSchema(o: Record<string, unknown>): boolean {
    if (o.type !== "object") {
        return false;
    }
    if (o.properties != null) {
        return false;
    }
    if (o.additionalProperties !== true) {
        return false;
    }
    const keys = Object.keys(o);
    return keys.every((k) => ["type", "additionalProperties", "description"].includes(k));
}

/**
 * 单看一份 schema：是否可用当前「表格入参」无损编辑。
 * 含 allOf/$ref/嵌套对象属性等时返回 false。
 */
export function isHttpParameterSchemaFormEditable(raw: unknown): boolean {
    if (raw === undefined || raw === null) {
        return true;
    }
    if (typeof raw !== "object" || Array.isArray(raw)) {
        return false;
    }
    const o = raw as Record<string, unknown>;
    const exotic = ["allOf", "oneOf", "anyOf", "not", "$ref", "prefixItems", "if", "then", "else", "dependentSchemas"];
    if (exotic.some((k) => o[k] != null)) {
        return false;
    }
    if (isDefaultLooseBackendParameterSchema(o)) {
        return true;
    }
    const allowedTop = new Set([
        "type",
        "properties",
        "required",
        "description",
        "title",
        "additionalProperties",
    ]);
    if (Object.keys(o).some((k) => !allowedTop.has(k))) {
        return false;
    }
    const props = o.properties;
    if (props == null) {
        return false;
    }
    if (typeof props !== "object" || Array.isArray(props)) {
        return false;
    }
    const propMap = props as Record<string, unknown>;
    const propKeys = Object.keys(propMap);
    const req = o.required;
    if (propKeys.length === 0) {
        if (Array.isArray(req) && req.length > 0) {
            return false;
        }
        return true;
    }
    const propAllowed = new Set(["type", "description", "default", "enum", "title"]);
    for (const pk of propKeys) {
        const spec = propMap[pk];
        if (spec != null && typeof spec === "object" && !Array.isArray(spec)) {
            const s = spec as Record<string, unknown>;
            if (Object.keys(s).some((k) => !propAllowed.has(k))) {
                return false;
            }
            const tp = s.type;
            if (tp != null) {
                if (typeof tp !== "string" || !(HTTP_PARAM_TYPES as readonly string[]).includes(tp)) {
                    return false;
                }
            }
        }
    }
    return true;
}

/**
 * 任一侧 parameters / inputSchema 存在且不能由表格无损表达时，保存应保留原字段。
 */
export function shouldPreserveHttpParameterFields(def: Record<string, unknown>): boolean {
    const flags: boolean[] = [];
    if (def.parameters !== undefined) {
        flags.push(isHttpParameterSchemaFormEditable(def.parameters));
    }
    if (def.inputSchema !== undefined) {
        flags.push(isHttpParameterSchemaFormEditable(def.inputSchema));
    }
    if (flags.length === 0) {
        return false;
    }
    return flags.some((ok) => !ok);
}

/** outputSchema 无法用表格无损表达时保留 */
export function shouldPreserveHttpOutputFields(def: Record<string, unknown>): boolean {
    if (def.outputSchema === undefined) {
        return false;
    }
    return !isHttpParameterSchemaFormEditable(def.outputSchema);
}

export function defaultHttpParameterRow(): HttpParameterRow {
    return {
        paramName: "",
        paramType: "string",
        required: false,
        paramDescription: "",
    };
}

/** 从 URL 模板中提取占位符名称（去重，顺序为首次出现顺序；与后端 HttpToolSpec `{param}` 一致） */
export function extractHttpUrlPlaceholderNames(url: string): string[] {
    const seen = new Set<string>();
    const out: string[] = [];
    if (!url) {
        return out;
    }
    for (const m of url.matchAll(/\{([a-zA-Z0-9_]+)\}/g)) {
        const name = m[1];
        if (name && !seen.has(name)) {
            seen.add(name);
            out.push(name);
        }
    }
    return out;
}

/**
 * URL 中每个 `{name}` 须在入参表格中有同名参数，否则模型不知道要填什么。
 */
export function validateHttpUrlPlaceholdersHaveParameterRows(
    url: string,
    rows: HttpParameterRow[] | undefined,
): string | null {
    const placeholders = extractHttpUrlPlaceholderNames(url.trim());
    if (placeholders.length === 0) {
        return null;
    }
    const declared = new Set(
        (rows ?? [])
            .map((r) => (r.paramName ?? "").trim())
            .filter(Boolean),
    );
    const missing = placeholders.filter((p) => !declared.has(p));
    if (missing.length === 0) {
        return null;
    }
    const show = missing.map((p) => `{${p}}`).join("、");
    return `URL 中的占位符 ${show} 未在「调用参数」中声明同名参数；请补全表格或修改 URL。`;
}

/** 保存前校验：名称合法、不重复（与后端 URL 占位符 `[a-zA-Z0-9_]+` 一致） */
export function validateHttpParameterRows(rows: HttpParameterRow[] | undefined): string | null {
    const names = (rows ?? [])
        .map((r) => (r.paramName ?? "").trim())
        .filter(Boolean);
    const seen = new Set<string>();
    for (const n of names) {
        if (!/^[a-zA-Z0-9_]+$/.test(n)) {
            return `参数名「${n}」无效：仅允许字母、数字、下划线，且须与 URL 中 {${n}} 占位符一致`;
        }
        if (seen.has(n)) {
            return `参数名重复：${n}`;
        }
        seen.add(n);
    }
    return null;
}

/** 出参字段名校验（不与 URL 占位符关联） */
export function validateHttpOutputFieldRows(rows: HttpParameterRow[] | undefined): string | null {
    const names = (rows ?? [])
        .map((r) => (r.paramName ?? "").trim())
        .filter(Boolean);
    const seen = new Set<string>();
    for (const n of names) {
        if (!/^[a-zA-Z0-9_]+$/.test(n)) {
            return `出参字段名「${n}」无效：仅允许字母、数字、下划线`;
        }
        if (seen.has(n)) {
            return `出参字段名重复：${n}`;
        }
        seen.add(n);
    }
    return null;
}

function headersFromRows(rows: HttpHeaderRow[] | undefined): Record<string, string> | undefined {
    if (!rows?.length) {
        return undefined;
    }
    const out: Record<string, string> = {};
    for (const row of rows) {
        const k = (row.headerName ?? "").trim();
        if (k) {
            out[k] = row.value ?? "";
        }
    }
    return Object.keys(out).length > 0 ? out : undefined;
}

function parametersSchemaFromRows(rows: HttpParameterRow[] | undefined): Record<string, unknown> | undefined {
    const valid = (rows ?? []).filter((r) => (r.paramName ?? "").trim());
    if (valid.length === 0) {
        return undefined;
    }
    const properties: Record<string, unknown> = {};
    const required: string[] = [];
    for (const r of valid) {
        const name = r.paramName.trim();
        const typ = HTTP_PARAM_TYPES.includes(r.paramType) ? r.paramType : "string";
        const desc = (r.paramDescription ?? "").trim();
        const prop: Record<string, unknown> = {type: typ};
        if (desc) {
            prop.description = desc;
        }
        properties[name] = prop;
        if (r.required) {
            required.push(name);
        }
    }
    const schema: Record<string, unknown> = {
        type: "object",
        properties,
    };
    if (required.length > 0) {
        schema.required = required;
    }
    return schema;
}

function mergeDefinition(
    built: Record<string, unknown>,
    existing: Record<string, unknown> | undefined,
    ownedKeys: Set<string>,
): Record<string, unknown> {
    const base: Record<string, unknown> = existing ? {...existing} : {};
    for (const k of ownedKeys) {
        delete base[k];
    }
    return {...base, ...built};
}

function rowsFromHeaders(headers: unknown): { headerName: string; value: string }[] {
    if (!headers || typeof headers !== "object" || Array.isArray(headers)) {
        return [];
    }
    return Object.entries(headers as Record<string, unknown>).map(([headerName, v]) => ({
        headerName,
        value: v == null ? "" : String(v),
    }));
}

/** 从任意 object schema 根节点还原表格行 */
export function rowsFromObjectSchemaRoot(raw: unknown): HttpParameterRow[] {
    if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
        return [defaultHttpParameterRow()];
    }
    const schema = raw as Record<string, unknown>;
    const props = schema.properties;
    if (!props || typeof props !== "object" || Array.isArray(props)) {
        return [defaultHttpParameterRow()];
    }
    const propMap = props as Record<string, unknown>;
    const keys = Object.keys(propMap);
    if (keys.length === 0) {
        return [defaultHttpParameterRow()];
    }
    const required = Array.isArray(schema.required)
        ? new Set((schema.required as unknown[]).map((x) => String(x)))
        : new Set<string>();
    return keys.map((name) => {
        const spec = propMap[name];
        const s = spec && typeof spec === "object" && !Array.isArray(spec) ? (spec as Record<string, unknown>) : {};
        const ty = typeof s.type === "string" ? s.type : "string";
        const paramType: HttpParamType = (HTTP_PARAM_TYPES as readonly string[]).includes(ty)
            ? (ty as HttpParamType)
            : "string";
        return {
            paramName: name,
            paramType,
            required: required.has(name),
            paramDescription: typeof s.description === "string" ? s.description : "",
        };
    });
}

/**
 * 从 definition.parameters 或 definition.inputSchema 还原表单行（与 HttpProxyAgentTool 读取顺序一致）。
 */
export function rowsFromParametersSchema(def: Record<string, unknown>): HttpParameterRow[] {
    return rowsFromObjectSchemaRoot(def.parameters ?? def.inputSchema);
}

export function rowsFromOutputSchema(def: Record<string, unknown>): HttpParameterRow[] {
    return rowsFromObjectSchemaRoot(def.outputSchema);
}

/**
 * 表单 → 提交给后端的 definition（与类型约定一致）。
 */
export function buildToolDefinition(values: ToolFormValues, options?: BuildDefinitionOptions): Record<string, unknown> | undefined {
    const existing = options?.existingDefinition;

    if (values.toolType === "HTTP") {
        const def: Record<string, unknown> = {
            url: values.httpUrl!.trim(),
            method: (values.httpMethod || "GET").toUpperCase(),
        };
        if (values.toolDescription?.trim()) {
            def.description = values.toolDescription.trim();
        }
        const hdrs = headersFromRows(values.httpHeaderRows);
        if (hdrs) {
            def.headers = hdrs;
        }
        const m = String(def.method);
        // POST/PUT/PATCH：必须显式写入 boolean。仅当 Switch 未挂载时 sendJsonBody 可能为 undefined，
        // 若只在与 false 相等时才写入，会漏掉 key；配合 Ant Design Switch 可能把 undefined 当成未勾选而提交 false。
        if (HTTP_METHODS_BODY.has(m)) {
            def.sendJsonBody = values.sendJsonBody !== false;
        }
        // GET/HEAD/DELETE：不写 sendJsonBody，merge 时会从旧 definition 中删掉该键（见 HTTP_OWNED_*）
        const preserveParams = values.httpParametersAdvancedPreserve === true;
        const preserveOutput = values.httpOutputSchemaAdvancedPreserve === true;
        if (!preserveParams) {
            const paramSchema = parametersSchemaFromRows(values.httpParameterRows);
            if (paramSchema) {
                def.parameters = paramSchema;
            }
        }
        if (!preserveOutput) {
            const outSchema = parametersSchemaFromRows(values.httpOutputParameterRows);
            if (outSchema) {
                def.outputSchema = outSchema;
            }
        }
        const httpOwned = buildHttpOwnedKeys(preserveParams, preserveOutput);
        const merged = mergeDefinition(def, existing, httpOwned);
        return Object.keys(merged).length > 0 ? merged : undefined;
    }

    if (values.toolType === "WORKFLOW") {
        const def: Record<string, unknown> = {
            workflowId: values.workflowId!.trim(),
        };
        if (values.toolDescription?.trim()) {
            def.description = values.toolDescription.trim();
        }
        const merged = mergeDefinition(def, existing, WORKFLOW_OWNED_KEYS);
        return Object.keys(merged).length > 0 ? merged : undefined;
    }

    if (values.toolType === "MCP") {
        const def: Record<string, unknown> = {};
        if (values.toolDescription?.trim()) {
            def.description = values.toolDescription.trim();
        }
        if (values.mcpEndpoint?.trim()) {
            def.endpoint = values.mcpEndpoint.trim();
        }
        if (values.mcpRemoteToolName?.trim()) {
            def.mcpToolName = values.mcpRemoteToolName.trim();
        }
        const preserveParams = values.mcpParametersAdvancedPreserve === true;
        if (!preserveParams) {
            const paramSchema = parametersSchemaFromRows(values.mcpParameterRows);
            if (paramSchema) {
                def.parameters = paramSchema;
            }
        }
        const merged = mergeDefinition(def, existing, buildMcpOwnedKeys(preserveParams));
        return Object.keys(merged).length > 0 ? merged : undefined;
    }

    // LOCAL
    const def: Record<string, unknown> = {};
    if (values.toolDescription?.trim()) {
        def.description = values.toolDescription.trim();
    }
    const merged = mergeDefinition(def, existing, LOCAL_OWNED_KEYS);
    return Object.keys(merged).length > 0 ? merged : undefined;
}

/**
 * 后端记录 → 表单初值（用于编辑）。
 */
export function toolDtoToFormValues(tool: ToolDto): Partial<ToolFormValues> {
    const raw = (tool.toolType || "LOCAL").toUpperCase();
    const t = (["LOCAL", "MCP", "HTTP", "WORKFLOW"].includes(raw) ? raw : "LOCAL") as ToolTypeCode;
    const def = {...(tool.definition ?? {})};
    const desc = typeof def.description === "string" ? def.description : undefined;

    const emptyHttpOutputs = {
        httpOutputParameterRows: [defaultHttpParameterRow()],
        httpOutputSchemaAdvancedPreserve: false,
    };

    if (t === "LOCAL") {
        return {
            toolType: "LOCAL",
            name: tool.name,
            toolDescription: desc,
            httpMethod: "GET",
            sendJsonBody: true,
            httpHeaderRows: [],
            httpParameterRows: [defaultHttpParameterRow()],
            ...emptyHttpOutputs,
        };
    }
    if (t === "MCP") {
        const preserveParams = shouldPreserveHttpParameterFields(def);
        const mcpParamRows = preserveParams ? [defaultHttpParameterRow()] : rowsFromParametersSchema(def);
        return {
            toolType: "MCP",
            name: tool.name,
            toolDescription: desc,
            mcpEndpoint: typeof def.endpoint === "string" ? def.endpoint : undefined,
            mcpRemoteToolName: typeof def.mcpToolName === "string" ? def.mcpToolName : undefined,
            mcpParameterRows: mcpParamRows.length ? mcpParamRows : [defaultHttpParameterRow()],
            mcpParametersAdvancedPreserve: preserveParams,
            httpMethod: "GET",
            sendJsonBody: true,
            httpHeaderRows: [],
            httpParameterRows: [defaultHttpParameterRow()],
            ...emptyHttpOutputs,
        };
    }
    if (t === "HTTP") {
        const preserve = shouldPreserveHttpParameterFields(def);
        const preserveOut = shouldPreserveHttpOutputFields(def);
        const paramRows = preserve ? [defaultHttpParameterRow()] : rowsFromParametersSchema(def);
        const outRows = preserveOut ? [defaultHttpParameterRow()] : rowsFromOutputSchema(def);
        return {
            toolType: "HTTP",
            name: tool.name,
            toolDescription: desc,
            httpUrl: String(def.url ?? ""),
            httpMethod: String(def.method ?? "GET"),
            sendJsonBody: def.sendJsonBody === false ? false : true,
            httpHeaderRows: rowsFromHeaders(def.headers),
            httpParameterRows: paramRows.length ? paramRows : [defaultHttpParameterRow()],
            httpParametersAdvancedPreserve: preserve,
            httpOutputParameterRows: outRows.length ? outRows : [defaultHttpParameterRow()],
            httpOutputSchemaAdvancedPreserve: preserveOut,
        };
    }
    if (t === "WORKFLOW") {
        return {
            toolType: "WORKFLOW",
            name: tool.name,
            toolDescription: desc,
            workflowId: String(def.workflowId ?? ""),
            httpMethod: "GET",
            sendJsonBody: true,
            httpHeaderRows: [],
            httpParameterRows: [defaultHttpParameterRow()],
            ...emptyHttpOutputs,
        };
    }
    return {
        toolType: "LOCAL",
        name: tool.name,
        httpMethod: "GET",
        sendJsonBody: true,
        httpHeaderRows: [],
        httpParameterRows: [defaultHttpParameterRow()],
        ...emptyHttpOutputs,
    };
}

export const HTTP_METHOD_OPTIONS = ["GET", "HEAD", "POST", "PUT", "PATCH", "DELETE"] as const;

export const NAME_ID_RULES = [
    {required: true, message: "请输入工具名称"},
    {
        pattern: /^[a-zA-Z][a-zA-Z0-9_-]*$/,
        message: "建议使用英文标识：字母开头，可含数字、下划线、短横线",
    },
];

/** 与后端 AgentScope Toolkit 注册及全平台唯一 name 策略一致 */
export const TOOL_NAME_AGENTSCOPE_HINT =
    "该名称会注册为 AgentScope Toolkit 工具名并参与模型 function calling；全平台唯一（与其它类型也不能重名，大小写不敏感）。";
