import type {
    HttpArrayItemPrimitiveType,
    HttpHeaderRow,
    HttpParameterRow,
    HttpParamType,
    ToolDto,
    ToolFormValues,
    ToolTypeCode,
} from "@/lib/tools/types";
import {HTTP_ARRAY_ITEM_PRIMITIVE_TYPES, HTTP_PARAM_TYPES} from "@/lib/tools/types";

const HTTP_PARAM_TYPE_LABELS: Record<string, string> = {
    string: "文本 string",
    number: "数字 number",
    integer: "整数 integer",
    boolean: "布尔 boolean",
    object: "对象 object",
    array: "数组 array",
};

/** HTTP 入参类型下拉 */
export const HTTP_PARAM_TYPE_OPTIONS = HTTP_PARAM_TYPES.map((t) => ({
    value: t,
    label: HTTP_PARAM_TYPE_LABELS[t] ?? t,
}));

/** 数组元素类型（基本类型或对象） */
export const HTTP_ARRAY_ITEM_TYPE_OPTIONS = HTTP_ARRAY_ITEM_PRIMITIVE_TYPES.map((t) => ({
    value: t,
    label: HTTP_PARAM_TYPE_LABELS[t] ?? t,
}));

/** 与表单编辑器、校验一致的最大嵌套层数（含 object 属性与 array items 对象属性） */
export const MAX_NESTED_HTTP_PARAM_DEPTH = 8;

const SCHEMA_EXOTIC_KEYS = new Set([
    "allOf",
    "oneOf",
    "anyOf",
    "not",
    "$ref",
    "prefixItems",
    "if",
    "then",
    "else",
    "dependentSchemas",
    "contains",
    "unevaluatedProperties",
]);

const LEAF_SCHEMA_KEYS = new Set([
    "type",
    "description",
    "title",
    "default",
    "enum",
    "format",
    "minimum",
    "maximum",
    "minLength",
    "maxLength",
]);

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

/** 解析 JSON Schema 的 type 字段（支持 string 或 ["string","null"] 等） */
function jsonSchemaTypeString(raw: unknown): string | undefined {
    if (typeof raw === "string") {
        return raw;
    }
    if (Array.isArray(raw)) {
        for (const x of raw) {
            if (typeof x === "string" && x !== "null") {
                return x;
            }
        }
    }
    return undefined;
}

/**
 * 从属性 schema 推断逻辑类型：缺省 type 但有 properties / items 时仍按 object / array 判断（与 rowFromPropertySchema 一致）。
 */
function effectivePropertySchemaType(s: Record<string, unknown>): string {
    const fromType = jsonSchemaTypeString(s.type);
    const props = s.properties;
    const hasProps = props != null && typeof props === "object" && !Array.isArray(props);
    const items = s.items;
    const hasItems = items != null && typeof items === "object" && !Array.isArray(items);
    if (fromType) {
        return fromType;
    }
    if (hasProps) {
        return "object";
    }
    if (hasItems) {
        return "array";
    }
    return "string";
}

/**
 * 单个 JSON Schema 属性节点是否可用当前表格递归编辑（支持 object / array 嵌套）。
 */
function isPropertySchemaNodeFormEditable(spec: unknown, depth: number): boolean {
    if (depth > MAX_NESTED_HTTP_PARAM_DEPTH) {
        return false;
    }
    if (spec == null || typeof spec !== "object" || Array.isArray(spec)) {
        return false;
    }
    const s = spec as Record<string, unknown>;
    if (Object.keys(s).some((k) => SCHEMA_EXOTIC_KEYS.has(k))) {
        return false;
    }
    const t = effectivePropertySchemaType(s);
    const scalar = new Set(["string", "number", "integer", "boolean"]);
    if (scalar.has(t)) {
        return Object.keys(s).every((k) => LEAF_SCHEMA_KEYS.has(k));
    }
    if (t === "object") {
        if (s.additionalProperties === true) {
            return false;
        }
        const allowed = new Set([
            "type",
            "properties",
            "required",
            "description",
            "title",
            "additionalProperties",
        ]);
        if (Object.keys(s).some((k) => !allowed.has(k))) {
            return false;
        }
        const props = s.properties;
        if (props == null) {
            return true;
        }
        if (typeof props !== "object" || Array.isArray(props)) {
            return false;
        }
        const propMap = props as Record<string, unknown>;
        for (const v of Object.values(propMap)) {
            if (!isPropertySchemaNodeFormEditable(v, depth + 1)) {
                return false;
            }
        }
        const req = s.required;
        return req == null || Array.isArray(req);
    }
    if (t === "array") {
        const allowed = new Set(["type", "items", "description", "title", "minItems", "maxItems"]);
        if (Object.keys(s).some((k) => !allowed.has(k))) {
            return false;
        }
        const items = s.items;
        if (items == null || Array.isArray(items) || typeof items !== "object") {
            return false;
        }
        return isPropertySchemaNodeFormEditable(items, depth + 1);
    }
    return false;
}

/**
 * 单看一份 schema：是否可用当前「表格入参」无损编辑（含嵌套 object / array）。
 * 含 allOf/$ref/tuple items 等时返回 false。
 */
export function isHttpParameterSchemaFormEditable(raw: unknown): boolean {
    if (raw === undefined || raw === null) {
        return true;
    }
    if (typeof raw !== "object" || Array.isArray(raw)) {
        return false;
    }
    const o = raw as Record<string, unknown>;
    if (Object.keys(o).some((k) => SCHEMA_EXOTIC_KEYS.has(k))) {
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
    if (o.type !== "object") {
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
    for (const pk of propKeys) {
        if (!isPropertySchemaNodeFormEditable(propMap[pk], 0)) {
            return false;
        }
    }
    return req == null || Array.isArray(req);
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

/** 仅顶层参数名（URL `{name}` 占位符只与顶层字段对齐，嵌套属性不参与） */
export function collectRootHttpParameterNames(rows: HttpParameterRow[] | undefined): string[] {
    return (rows ?? [])
        .map((r) => (r.paramName ?? "").trim())
        .filter(Boolean);
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
    const declared = new Set(collectRootHttpParameterNames(rows));
    const missing = placeholders.filter((p) => !declared.has(p));
    if (missing.length === 0) {
        return null;
    }
    const show = missing.map((p) => `{${p}}`).join("、");
    return `URL 中的占位符 ${show} 未在「调用参数」中声明同名参数；请补全表格或修改 URL。`;
}

/** 保存前校验：名称合法、不重复（与后端 URL 占位符 `[a-zA-Z0-9_]+` 一致）；递归校验嵌套 */
export function validateHttpParameterRows(rows: HttpParameterRow[] | undefined): string | null {
    return validateHttpParameterRowsAtLevel(rows, 0);
}

function validateHttpParameterRowsAtLevel(rows: HttpParameterRow[] | undefined, depth: number): string | null {
    if (depth > MAX_NESTED_HTTP_PARAM_DEPTH) {
        return `参数嵌套超过 ${MAX_NESTED_HTTP_PARAM_DEPTH} 层，请简化或使用「高级入参」保留 JSON Schema`;
    }
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
    for (const r of rows ?? []) {
        const nm = (r.paramName ?? "").trim();
        if (!nm) {
            continue;
        }
        if (r.paramType === "object") {
            const err = validateHttpParameterRowsAtLevel(r.children, depth + 1);
            if (err) {
                return err;
            }
        }
        if (r.paramType === "array") {
            const el = r.arrayItemsPrimitiveType ?? "string";
            if (el === "object") {
                const err = validateHttpParameterRowsAtLevel(r.arrayItemProperties, depth + 1);
                if (err) {
                    return err;
                }
            }
        }
    }
    return null;
}

/** 出参字段名校验（不与 URL 占位符关联）；支持嵌套 object/array */
export function validateHttpOutputFieldRows(rows: HttpParameterRow[] | undefined): string | null {
    return validateHttpOutputFieldRowsAtLevel(rows, 0);
}

function validateHttpOutputFieldRowsAtLevel(rows: HttpParameterRow[] | undefined, depth: number): string | null {
    if (depth > MAX_NESTED_HTTP_PARAM_DEPTH) {
        return `出参嵌套超过 ${MAX_NESTED_HTTP_PARAM_DEPTH} 层，请简化或使用高级 outputSchema`;
    }
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
    for (const r of rows ?? []) {
        if (!(r.paramName ?? "").trim()) {
            continue;
        }
        if (r.paramType === "object") {
            const err = validateHttpOutputFieldRowsAtLevel(r.children, depth + 1);
            if (err) {
                return err;
            }
        }
        if (r.paramType === "array") {
            const el = r.arrayItemsPrimitiveType ?? "string";
            if (el === "object") {
                const err = validateHttpOutputFieldRowsAtLevel(r.arrayItemProperties, depth + 1);
                if (err) {
                    return err;
                }
            }
        }
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

function buildPropertySchemaFromRow(r: HttpParameterRow): Record<string, unknown> | undefined {
    const name = (r.paramName ?? "").trim();
    if (!name) {
        return undefined;
    }
    const desc = (r.paramDescription ?? "").trim();
    const withDesc = (o: Record<string, unknown>) => {
        if (desc) {
            o.description = desc;
        }
        return o;
    };
    switch (r.paramType) {
        case "string":
        case "number":
        case "integer":
        case "boolean":
            return withDesc({type: r.paramType});
        case "object": {
            const inner = parametersSchemaFromRows(r.children);
            const o: Record<string, unknown> = {type: "object"};
            if (inner?.properties != null) {
                o.properties = inner.properties;
            } else {
                o.properties = {};
            }
            if (Array.isArray(inner?.required) && (inner.required as string[]).length > 0) {
                o.required = inner.required;
            }
            return withDesc(o);
        }
        case "array": {
            const prim = r.arrayItemsPrimitiveType ?? "string";
            let items: Record<string, unknown>;
            if (prim === "object") {
                const inner = parametersSchemaFromRows(r.arrayItemProperties);
                items = {type: "object"};
                if (inner?.properties != null) {
                    items.properties = inner.properties;
                } else {
                    items.properties = {};
                }
                if (Array.isArray(inner?.required) && (inner.required as string[]).length > 0) {
                    items.required = inner.required;
                }
            } else {
                items = {type: prim};
            }
            return withDesc({type: "array", items});
        }
        default:
            return withDesc({type: "string"});
    }
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
        const prop = buildPropertySchemaFromRow(r);
        if (!prop) {
            continue;
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

/** 编辑/回显前规范化嵌套行：补全 boolean、子数组，避免 Form 受控组件异常 */
export function normalizeHttpParameterRowTree(rows: HttpParameterRow[] | undefined): HttpParameterRow[] {
    const list = rows?.length ? rows : [];
    return list.map((r) => normalizeHttpParameterRowOne(r));
}

function normalizeHttpParameterRowOne(r: HttpParameterRow): HttpParameterRow {
    const paramType = r.paramType ?? "string";
    const out: HttpParameterRow = {
        paramName: r.paramName ?? "",
        paramType: (HTTP_PARAM_TYPES as readonly string[]).includes(paramType) ? (paramType as HttpParamType) : "string",
        required: r.required === true,
        paramDescription: typeof r.paramDescription === "string" ? r.paramDescription : "",
    };
    if (out.paramType === "object") {
        out.children = normalizeHttpParameterRowTree(r.children);
    }
    if (out.paramType === "array") {
        const prim = r.arrayItemsPrimitiveType ?? "string";
        out.arrayItemsPrimitiveType = (HTTP_ARRAY_ITEM_PRIMITIVE_TYPES as readonly string[]).includes(
            prim as HttpArrayItemPrimitiveType,
        )
            ? (prim as HttpArrayItemPrimitiveType)
            : "string";
        if (out.arrayItemsPrimitiveType === "object") {
            out.arrayItemProperties = normalizeHttpParameterRowTree(r.arrayItemProperties);
        }
    }
    return out;
}

function rowFromPropertySchema(name: string, spec: unknown, required: Set<string>): HttpParameterRow {
    const s = spec && typeof spec === "object" && !Array.isArray(spec) ? (spec as Record<string, unknown>) : {};
    const ty = effectivePropertySchemaType(s);
    const desc = typeof s.description === "string" ? s.description : "";
    const base = {
        paramName: name,
        required: required.has(name),
        paramDescription: desc,
    };

    if (ty === "object") {
        const props = s.properties;
        let children: HttpParameterRow[] | undefined;
        if (props && typeof props === "object" && !Array.isArray(props)) {
            children = rowsFromObjectSchemaRoot({
                type: "object",
                properties: props,
                required: s.required,
            });
        }
        return {
            ...base,
            paramType: "object",
            children: children?.length ? children : [],
        };
    }

    if (ty === "array") {
        const items = s.items;
        if (items && typeof items === "object" && !Array.isArray(items)) {
            const im = items as Record<string, unknown>;
            const itemTy = jsonSchemaTypeString(im.type);
            const ip = im.properties;
            const hasItemProps = ip != null && typeof ip === "object" && !Array.isArray(ip);
            const it = itemTy ?? (hasItemProps ? "object" : "string");
            if (it === "object") {
                let arrayItemProperties: HttpParameterRow[] | undefined;
                if (hasItemProps && ip && typeof ip === "object" && !Array.isArray(ip)) {
                    arrayItemProperties = rowsFromObjectSchemaRoot({
                        type: "object",
                        properties: ip,
                        required: im.required,
                    });
                }
                return {
                    ...base,
                    paramType: "array",
                    arrayItemsPrimitiveType: "object",
                    arrayItemProperties: arrayItemProperties?.length ? arrayItemProperties : [],
                };
            }
            const apt = (HTTP_ARRAY_ITEM_PRIMITIVE_TYPES as readonly string[]).includes(it as HttpArrayItemPrimitiveType)
                ? (it as HttpArrayItemPrimitiveType)
                : "string";
            return {
                ...base,
                paramType: "array",
                arrayItemsPrimitiveType: apt === "object" ? "string" : apt,
            };
        }
        return {
            ...base,
            paramType: "array",
            arrayItemsPrimitiveType: "string",
        };
    }

    const paramType: HttpParamType = (HTTP_PARAM_TYPES as readonly string[]).includes(ty)
        ? (ty as HttpParamType)
        : "string";
    return {
        ...base,
        paramType,
    };
}

/** 从任意 object schema 根节点还原表格行（递归 object / array） */
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
    return keys.map((name) => rowFromPropertySchema(name, propMap[name], required));
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
    const cat =
        String(tool.toolCategory ?? "ACTION").toUpperCase() === "QUERY" ? ("QUERY" as const) : ("ACTION" as const);
    const displayLabel = typeof tool.displayLabel === "string" ? tool.displayLabel : undefined;
    const platformDescription = typeof tool.description === "string" ? tool.description : undefined;
    const def = {...(tool.definition ?? {})};
    const desc = typeof def.description === "string" ? def.description : undefined;

    const emptyHttpOutputs = {
        httpOutputParameterRows: [defaultHttpParameterRow()],
        httpOutputSchemaAdvancedPreserve: false,
    };

    if (t === "LOCAL") {
        return {
            toolType: "LOCAL",
            toolCategory: cat,
            name: tool.name,
            displayLabel,
            platformDescription,
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
        const mcpParamRows = preserveParams
            ? [defaultHttpParameterRow()]
            : normalizeHttpParameterRowTree(
                (() => {
                    const raw = rowsFromParametersSchema(def);
                    return raw.length ? raw : [defaultHttpParameterRow()];
                })(),
            );
        return {
            toolType: "MCP",
            toolCategory: cat,
            name: tool.name,
            displayLabel,
            platformDescription,
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
        const paramRows = preserve
            ? [defaultHttpParameterRow()]
            : normalizeHttpParameterRowTree(
                (() => {
                    const raw = rowsFromParametersSchema(def);
                    return raw.length ? raw : [defaultHttpParameterRow()];
                })(),
            );
        const outRows = preserveOut
            ? [defaultHttpParameterRow()]
            : normalizeHttpParameterRowTree(
                (() => {
                    const raw = rowsFromOutputSchema(def);
                    return raw.length ? raw : [defaultHttpParameterRow()];
                })(),
            );
        return {
            toolType: "HTTP",
            toolCategory: cat,
            name: tool.name,
            displayLabel,
            platformDescription,
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
            toolCategory: cat,
            name: tool.name,
            displayLabel,
            platformDescription,
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
        toolCategory: cat,
        name: tool.name,
        displayLabel,
        platformDescription,
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

/** 与后端运行时工具注册及全平台唯一 name 策略一致 */
export const TOOL_NAME_RUNTIME_HINT =
    "该名称将作为运行时工具标识并参与模型侧工具调用；全平台唯一（与其它类型也不能重名，大小写不敏感）。";
