import type {
    HttpArrayItemPrimitiveType,
    HttpHeaderRow,
    HttpParameterRow,
    HttpParamType,
    LocalBuiltinToolMetaDto,
    ParamValueSourceType,
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

/** 入参「值来源」说明（仅元数据，写入 x-agentlego-valueSource） */
export const PARAM_VALUE_SOURCE_OPTIONS: { value: ParamValueSourceType; label: string }[] = [
    {value: "CONTEXT", label: "上下文参数"},
    {value: "MODEL", label: "模型提取"},
    {value: "FIXED", label: "固定值"},
];

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
    /** 入参值来源说明（CONTEXT / MODEL / FIXED），仅描述用 */
    "x-agentlego-valueSource",
    /** 固定值说明（与 FIXED 搭配），仅描述用 */
    "x-agentlego-fixedValue",
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
        s.add("parameterAliases");
    }
    if (!preserveOutput) {
        for (const k of HTTP_OUTPUT_SCHEMA_KEYS) {
            s.add(k);
        }
    }
    return s;
}

function buildWorkflowOwnedKeys(preserveParams: boolean, preserveOutput: boolean): Set<string> {
    const s = new Set<string>(["workflowId", "description"]);
    if (!preserveParams) {
        s.add("parameters");
        s.add("inputSchema");
        s.add("parameterAliases");
    }
    if (!preserveOutput) {
        s.add("outputSchema");
    }
    return s;
}

function buildMcpOwnedKeys(preserveParams: boolean, preserveOutput: boolean): Set<string> {
    const s = new Set<string>(["description", "endpoint", "mcpToolName"]);
    if (!preserveParams) {
        s.add("parameters");
        s.add("inputSchema");
    }
    if (!preserveOutput) {
        s.add("outputSchema");
    }
    return s;
}

function buildLocalOwnedKeys(preserveParams: boolean, preserveOutput: boolean): Set<string> {
    const s = new Set<string>(["description"]);
    if (!preserveParams) {
        s.add("parameters");
        s.add("inputSchema");
        s.add("parameterAliases");
    }
    if (!preserveOutput) {
        s.add("outputSchema");
    }
    return s;
}

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
            "x-agentlego-valueSource",
            "x-agentlego-fixedValue",
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
        const allowed = new Set([
            "type",
            "items",
            "description",
            "title",
            "minItems",
            "maxItems",
            "x-agentlego-valueSource",
            "x-agentlego-fixedValue",
        ]);
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

/**
 * 根级 string/number 等简单出参（如 LOCAL 内置默认），无 properties，可用空表格承接，不应判为「高级保留」。
 */
function isScalarRootOutputSchemaForForm(raw: unknown): boolean {
    if (raw === undefined || raw === null || typeof raw !== "object" || Array.isArray(raw)) {
        return false;
    }
    const o = raw as Record<string, unknown>;
    const ts = jsonSchemaTypeString(o.type);
    if (!ts || !["string", "number", "integer", "boolean"].includes(ts)) {
        return false;
    }
    const props = o.properties;
    if (props != null && typeof props === "object" && !Array.isArray(props) && Object.keys(props).length > 0) {
        return false;
    }
    const keys = Object.keys(o);
    return keys.every((k) => ["type", "description", "title"].includes(k));
}

/** outputSchema 无法用表格无损表达时保留 */
export function shouldPreserveHttpOutputFields(def: Record<string, unknown>): boolean {
    if (def.outputSchema === undefined) {
        return false;
    }
    if (isScalarRootOutputSchemaForForm(def.outputSchema)) {
        return false;
    }
    return !isHttpParameterSchemaFormEditable(def.outputSchema);
}

export function defaultHttpParameterRow(): HttpParameterRow {
    return {
        paramName: "",
        paramAlias: "",
        valueSource: "MODEL",
        fixedValue: "",
        paramType: "string",
        required: false,
        paramDescription: "",
    };
}

function mapJavaSimpleTypeToHttpParamType(t: string | undefined): HttpParamType {
    const u = String(t ?? "").toLowerCase();
    if (u.includes("int") || u === "long" || u === "short" || u === "byte") {
        return "integer";
    }
    if (u === "double" || u === "float" || u === "number" || u === "bigdecimal") {
        return "number";
    }
    if (u === "boolean") {
        return "boolean";
    }
    return "string";
}

/**
 * 新建 LOCAL 工具时，根据内置元数据预填「入参」表单行（与内置 inputSchema / inputParameters 对齐）。
 */
export function seedLocalParameterRowsFromBuiltin(meta: LocalBuiltinToolMetaDto): HttpParameterRow[] {
    const schema = meta.inputSchema;
    if (schema && typeof schema === "object" && !Array.isArray(schema) && Object.keys(schema as object).length > 0) {
        const raw = rowsFromObjectSchemaRoot(schema);
        return normalizeHttpParameterRowTree(raw.length ? raw : [defaultHttpParameterRow()]);
    }
    const ips = meta.inputParameters ?? [];
    if (ips.length === 0) {
        return [defaultHttpParameterRow()];
    }
    const rows: HttpParameterRow[] = ips.map((p) => ({
        paramName: p.name ?? "",
        paramAlias: "",
        valueSource: "MODEL",
        fixedValue: "",
        paramType: mapJavaSimpleTypeToHttpParamType(p.type),
        required: !!p.required,
        paramDescription: typeof p.description === "string" ? p.description : "",
    }));
    return normalizeHttpParameterRowTree(rows);
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

/** 顶层入参别名（可与 URL 占位符一致） */
export function collectRootHttpParameterAliases(rows: HttpParameterRow[] | undefined): string[] {
    return (rows ?? [])
        .map((r) => (r.paramAlias ?? "").trim())
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
    const declared = new Set([...collectRootHttpParameterNames(rows), ...collectRootHttpParameterAliases(rows)]);
    const missing = placeholders.filter((p) => !declared.has(p));
    if (missing.length === 0) {
        return null;
    }
    const show = missing.map((p) => `{${p}}`).join("、");
    return `URL 中的占位符 ${show} 未在「调用参数」中声明同名参数或别名；请补全表格或修改 URL。`;
}

/** 顶层入参别名：与参数名互斥冲突校验 */
function validateRootParameterAliases(rows: HttpParameterRow[] | undefined): string | null {
    const list = rows ?? [];
    const wireNames = new Set<string>();
    for (const r of list) {
        const name = (r.paramName ?? "").trim();
        const alias = (r.paramAlias ?? "").trim();
        if (!name) {
            continue;
        }
        if (alias && alias !== name) {
            if (!/^[a-zA-Z0-9_]+$/.test(alias)) {
                return `别名「${alias}」无效：仅允许字母、数字、下划线`;
            }
            if (wireNames.has(alias)) {
                return `别名「${alias}」重复`;
            }
            wireNames.add(alias);
        }
    }
    for (const r of list) {
        const name = (r.paramName ?? "").trim();
        const alias = (r.paramAlias ?? "").trim();
        if (!name || !alias || alias === name) {
            continue;
        }
        for (const o of list) {
            const on = (o.paramName ?? "").trim();
            if (on && on === alias && on !== name) {
                return `别名「${alias}」与参数名「${on}」冲突`;
            }
        }
    }
    return null;
}

/** 保存前校验：名称合法、不重复（与后端 URL 占位符 `[a-zA-Z0-9_]+` 一致）；递归校验嵌套 */
export function validateHttpParameterRows(rows: HttpParameterRow[] | undefined): string | null {
    return validateHttpParameterRowsAtLevel(rows, 0);
}

function validateHttpParameterRowsAtLevel(rows: HttpParameterRow[] | undefined, depth: number): string | null {
    if (depth > MAX_NESTED_HTTP_PARAM_DEPTH) {
        return `参数嵌套超过 ${MAX_NESTED_HTTP_PARAM_DEPTH} 层，请简化或使用「高级入参」保留 JSON Schema`;
    }
    if (depth === 0) {
        const aliasErr = validateRootParameterAliases(rows);
        if (aliasErr) {
            return aliasErr;
        }
    }
    const names = (rows ?? [])
        .map((r) => (r.paramName ?? "").trim())
        .filter(Boolean);
    const seen = new Set<string>();
    for (const n of names) {
        if (!/^[a-zA-Z0-9_]+$/.test(n)) {
            return `参数名「${n}」无效：仅允许字母、数字、下划线（可与 URL 占位符或别名对齐）`;
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
        if (r.valueSource === "FIXED") {
            const fv = (r.fixedValue ?? "").trim();
            if (!fv) {
                return `参数「${nm}」为固定值类型时，请填写固定值`;
            }
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

/** 从属性 schema 读取入参「值来源」元数据（缺省为 MODEL） */
export function readValueSourceFromSchema(s: Record<string, unknown>): ParamValueSourceType {
    const raw = s["x-agentlego-valueSource"];
    if (raw === "CONTEXT" || raw === "MODEL" || raw === "FIXED") {
        return raw;
    }
    return "MODEL";
}

/** 值来源枚举对应的中文标签（用于详情表展示） */
export function paramValueSourceLabel(vs: ParamValueSourceType): string {
    return PARAM_VALUE_SOURCE_OPTIONS.find((o) => o.value === vs)?.label ?? vs;
}

/** 从属性 schema 读取固定值元数据（字符串） */
export function readFixedValueFromSchema(s: Record<string, unknown>): string {
    const raw = s["x-agentlego-fixedValue"];
    return typeof raw === "string" ? raw : "";
}

/** 入参值来源元数据（仅描述，不参与 HTTP 执行逻辑） */
function applyValueSourceMeta(o: Record<string, unknown>, r: HttpParameterRow) {
    const vs = r.valueSource ?? "MODEL";
    o["x-agentlego-valueSource"] = vs;
}

/** 固定值说明（仅 valueSource=FIXED 时写入） */
function applyFixedValueMeta(o: Record<string, unknown>, r: HttpParameterRow) {
    const vs = r.valueSource ?? "MODEL";
    if (vs === "FIXED") {
        const v = (r.fixedValue ?? "").trim();
        if (v) {
            o["x-agentlego-fixedValue"] = v;
        }
    }
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
    let out: Record<string, unknown>;
    switch (r.paramType) {
        case "string":
        case "number":
        case "integer":
        case "boolean":
            out = withDesc({type: r.paramType});
            break;
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
            out = withDesc(o);
            break;
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
            out = withDesc({type: "array", items});
            break;
        }
        default:
            out = withDesc({type: "string"});
    }
    applyValueSourceMeta(out, r);
    applyFixedValueMeta(out, r);
    return out;
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

function parameterAliasesFromRootRows(rows: HttpParameterRow[] | undefined): Record<string, string> | undefined {
    const out: Record<string, string> = {};
    for (const r of rows ?? []) {
        const name = (r.paramName ?? "").trim();
        const alias = (r.paramAlias ?? "").trim();
        if (!name || !alias || alias === name) {
            continue;
        }
        out[name] = alias;
    }
    return Object.keys(out).length > 0 ? out : undefined;
}

function applyParameterAliasesToRows(rows: HttpParameterRow[], aliases: unknown): HttpParameterRow[] {
    if (!aliases || typeof aliases !== "object" || Array.isArray(aliases)) {
        return rows;
    }
    const map = aliases as Record<string, unknown>;
    return rows.map((r) => {
        const name = (r.paramName ?? "").trim();
        const a = map[name];
        if (typeof a === "string" && a.trim() && a.trim() !== name) {
            return {...r, paramAlias: a.trim()};
        }
        return r;
    });
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
    const vs = r.valueSource;
    const valueSource: ParamValueSourceType =
        vs === "CONTEXT" || vs === "MODEL" || vs === "FIXED" ? vs : "MODEL";
    const out: HttpParameterRow = {
        paramName: r.paramName ?? "",
        paramAlias: typeof r.paramAlias === "string" ? r.paramAlias : "",
        valueSource,
        fixedValue: typeof r.fixedValue === "string" ? r.fixedValue : "",
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
        valueSource: readValueSourceFromSchema(s),
        fixedValue: readFixedValueFromSchema(s),
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
            const aliases = parameterAliasesFromRootRows(values.httpParameterRows);
            if (aliases) {
                def.parameterAliases = aliases;
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
        const preserveParams = values.workflowParametersAdvancedPreserve === true;
        const preserveOut = values.workflowOutputSchemaAdvancedPreserve === true;
        if (!preserveParams) {
            const paramSchema = parametersSchemaFromRows(values.workflowParameterRows);
            if (paramSchema) {
                def.parameters = paramSchema;
            }
            const aliases = parameterAliasesFromRootRows(values.workflowParameterRows);
            if (aliases) {
                def.parameterAliases = aliases;
            }
        }
        if (!preserveOut) {
            const outSchema = parametersSchemaFromRows(values.workflowOutputParameterRows);
            if (outSchema) {
                def.outputSchema = outSchema;
            }
        }
        const merged = mergeDefinition(def, existing, buildWorkflowOwnedKeys(preserveParams, preserveOut));
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
            const aliases = parameterAliasesFromRootRows(values.mcpParameterRows);
            if (aliases) {
                def.parameterAliases = aliases;
            }
        }
        const preserveOut = values.mcpOutputSchemaAdvancedPreserve === true;
        if (!preserveOut) {
            const outSchema = parametersSchemaFromRows(values.mcpOutputParameterRows);
            if (outSchema) {
                def.outputSchema = outSchema;
            }
        }
        const merged = mergeDefinition(def, existing, buildMcpOwnedKeys(preserveParams, preserveOut));
        return Object.keys(merged).length > 0 ? merged : undefined;
    }

    // LOCAL
    const def: Record<string, unknown> = {};
    if (values.toolDescription?.trim()) {
        def.description = values.toolDescription.trim();
    }
    const preserveParams = values.localParametersAdvancedPreserve === true;
    const preserveOut = values.localOutputSchemaAdvancedPreserve === true;
    if (!preserveParams) {
        const paramSchema = parametersSchemaFromRows(values.localParameterRows);
        if (paramSchema) {
            def.parameters = paramSchema;
        }
        const aliases = parameterAliasesFromRootRows(values.localParameterRows);
        if (aliases) {
            def.parameterAliases = aliases;
        }
    }
    if (!preserveOut) {
        const outSchema = parametersSchemaFromRows(values.localOutputParameterRows);
        if (outSchema) {
            def.outputSchema = outSchema;
        }
    }
    const merged = mergeDefinition(def, existing, buildLocalOwnedKeys(preserveParams, preserveOut));
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
        const preserveParams = shouldPreserveHttpParameterFields(def);
        const preserveOut = shouldPreserveHttpOutputFields(def);
        const localParamRowsRaw = preserveParams
            ? [defaultHttpParameterRow()]
            : normalizeHttpParameterRowTree(
                  (() => {
                      const raw = rowsFromParametersSchema(def);
                      return raw.length ? raw : [defaultHttpParameterRow()];
                  })(),
              );
        const localParamRows = preserveParams
            ? localParamRowsRaw
            : applyParameterAliasesToRows(localParamRowsRaw, def.parameterAliases);
        const outRows = preserveOut
            ? [defaultHttpParameterRow()]
            : normalizeHttpParameterRowTree(
                  (() => {
                      const raw = rowsFromOutputSchema(def);
                      return raw.length ? raw : [defaultHttpParameterRow()];
                  })(),
              );
        return {
            toolType: "LOCAL",
            toolCategory: cat,
            name: tool.name,
            displayLabel,
            platformDescription,
            toolDescription: desc,
            localParameterRows: localParamRows.length ? localParamRows : [defaultHttpParameterRow()],
            localParametersAdvancedPreserve: preserveParams,
            localOutputParameterRows: outRows.length ? outRows : [defaultHttpParameterRow()],
            localOutputSchemaAdvancedPreserve: preserveOut,
            httpMethod: "GET",
            sendJsonBody: true,
            httpHeaderRows: [],
            httpParameterRows: [defaultHttpParameterRow()],
            ...emptyHttpOutputs,
        };
    }
    if (t === "MCP") {
        const preserveParams = shouldPreserveHttpParameterFields(def);
        const preserveOut = shouldPreserveHttpOutputFields(def);
        const mcpParamRowsRaw = preserveParams
            ? [defaultHttpParameterRow()]
            : normalizeHttpParameterRowTree(
                (() => {
                    const raw = rowsFromParametersSchema(def);
                    return raw.length ? raw : [defaultHttpParameterRow()];
                })(),
            );
        const mcpParamRows = preserveParams
            ? mcpParamRowsRaw
            : applyParameterAliasesToRows(mcpParamRowsRaw, def.parameterAliases);
        const mcpOutRows = preserveOut
            ? [defaultHttpParameterRow()]
            : normalizeHttpParameterRowTree(
                (() => {
                    const raw = rowsFromOutputSchema(def);
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
            mcpOutputParameterRows: mcpOutRows.length ? mcpOutRows : [defaultHttpParameterRow()],
            mcpOutputSchemaAdvancedPreserve: preserveOut,
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
        const paramRowsRaw = preserve
            ? [defaultHttpParameterRow()]
            : normalizeHttpParameterRowTree(
                (() => {
                    const raw = rowsFromParametersSchema(def);
                    return raw.length ? raw : [defaultHttpParameterRow()];
                })(),
            );
        const paramRows = preserve ? paramRowsRaw : applyParameterAliasesToRows(paramRowsRaw, def.parameterAliases);
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
        const preserveParams = shouldPreserveHttpParameterFields(def);
        const preserveOut = shouldPreserveHttpOutputFields(def);
        const wfParamRowsRaw = preserveParams
            ? [defaultHttpParameterRow()]
            : normalizeHttpParameterRowTree(
                (() => {
                    const raw = rowsFromParametersSchema(def);
                    return raw.length ? raw : [defaultHttpParameterRow()];
                })(),
            );
        const wfParamRows = preserveParams
            ? wfParamRowsRaw
            : applyParameterAliasesToRows(wfParamRowsRaw, def.parameterAliases);
        const wfOutRows = preserveOut
            ? [defaultHttpParameterRow()]
            : normalizeHttpParameterRowTree(
                (() => {
                    const raw = rowsFromOutputSchema(def);
                    return raw.length ? raw : [defaultHttpParameterRow()];
                })(),
            );
        return {
            toolType: "WORKFLOW",
            toolCategory: cat,
            name: tool.name,
            displayLabel,
            platformDescription,
            toolDescription: desc,
            workflowId: String(def.workflowId ?? ""),
            workflowParameterRows: wfParamRows.length ? wfParamRows : [defaultHttpParameterRow()],
            workflowParametersAdvancedPreserve: preserveParams,
            workflowOutputParameterRows: wfOutRows.length ? wfOutRows : [defaultHttpParameterRow()],
            workflowOutputSchemaAdvancedPreserve: preserveOut,
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
