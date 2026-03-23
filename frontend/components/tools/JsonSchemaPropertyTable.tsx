"use client";

import {Table, Tag, Typography} from "antd";
import type {ColumnsType} from "antd/es/table";
import React from "react";

import {
    isHttpParameterSchemaFormEditable,
    paramValueSourceLabel,
    readFixedValueFromSchema,
    readValueSourceFromSchema,
} from "@/lib/tools/form";

type SchemaTableRole = "input" | "output";

const MAX_SCHEMA_DETAIL_DEPTH = 14;

function asSchemaRecord(spec: unknown): Record<string, unknown> {
    return spec && typeof spec === "object" && !Array.isArray(spec) ? (spec as Record<string, unknown>) : {};
}

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

function requiredArrayToSet(raw: unknown): Set<string> {
    if (!Array.isArray(raw)) {
        return new Set();
    }
    return new Set(raw.map((x) => String(x)));
}

function effectiveSchemaNodeType(s: Record<string, unknown>): string {
    const fromType = jsonSchemaTypeString(s.type);
    if (fromType) {
        return fromType;
    }
    const p = s.properties;
    if (p != null && typeof p === "object" && !Array.isArray(p)) {
        return "object";
    }
    const it = s.items;
    if (it != null && typeof it === "object" && !Array.isArray(it)) {
        return "array";
    }
    return "—";
}

function describeItemsShape(im: Record<string, unknown>): string {
    const t = effectiveSchemaNodeType(im);
    if (t === "array") {
        const inner = im.items;
        if (inner && typeof inner === "object" && !Array.isArray(inner)) {
            return `array<${describeItemsShape(inner as Record<string, unknown>)}>`;
        }
        return "array";
    }
    if (t === "object") {
        const p = im.properties;
        const n = p && typeof p === "object" && !Array.isArray(p) ? Object.keys(p as object).length : 0;
        return n > 0 ? `object{${n} 字段}` : "object";
    }
    return t;
}

function describeSchemaNode(s: Record<string, unknown>): string {
    const t = effectiveSchemaNodeType(s);
    if (t === "array") {
        const items = s.items;
        if (items && typeof items === "object" && !Array.isArray(items)) {
            return `array<${describeItemsShape(items as Record<string, unknown>)}>`;
        }
        return "array";
    }
    if (t === "object") {
        const p = s.properties;
        const n = p && typeof p === "object" && !Array.isArray(p) ? Object.keys(p as object).length : 0;
        return n > 0 ? `object{${n} 字段}` : "object";
    }
    return t;
}

type NestedParamRow = {
    rowKey: string;
    path: string;
    typ: string;
    required: boolean;
    /** 入参详情表用：x-agentlego-valueSource 的中文说明 */
    valueSourceLabel: string;
    /** 入参详情表用：x-agentlego-fixedValue 展示 */
    fixedValueDisplay: string;
    desc: string;
    depth: number;
};

function flattenSchemaPropertyRows(
    properties: Record<string, unknown>,
    requiredKeys: Set<string>,
    pathPrefix: string,
    depth: number,
): NestedParamRow[] {
    if (depth > MAX_SCHEMA_DETAIL_DEPTH) {
        return [];
    }
    const rows: NestedParamRow[] = [];
    for (const name of Object.keys(properties)) {
        const spec = properties[name];
        const s = asSchemaRecord(spec);
        const path = pathPrefix ? `${pathPrefix}.${name}` : name;
        const typ = describeSchemaNode(s);
        const desc = typeof s.description === "string" ? s.description : "—";
        const valueSourceLabel = paramValueSourceLabel(readValueSourceFromSchema(s));
        const fv = readFixedValueFromSchema(s).trim();
        const fixedValueDisplay = fv || "—";
        rows.push({
            rowKey: path,
            path,
            typ,
            required: requiredKeys.has(name),
            valueSourceLabel,
            fixedValueDisplay,
            desc,
            depth,
        });
        if (depth >= MAX_SCHEMA_DETAIL_DEPTH) {
            continue;
        }
        const childProps = s.properties;
        if (childProps && typeof childProps === "object" && !Array.isArray(childProps)) {
            const childReq = requiredArrayToSet(s.required);
            rows.push(
                ...flattenSchemaPropertyRows(
                    childProps as Record<string, unknown>,
                    childReq,
                    path,
                    depth + 1,
                ),
            );
        }
        const items = s.items;
        if (items && typeof items === "object" && !Array.isArray(items)) {
            const im = items as Record<string, unknown>;
            if (effectiveSchemaNodeType(im) === "object") {
                const ip = im.properties;
                if (ip && typeof ip === "object" && !Array.isArray(ip)) {
                    const childReq = requiredArrayToSet(im.required);
                    const arrayPath = `${path}[]`;
                    rows.push(
                        ...flattenSchemaPropertyRows(
                            ip as Record<string, unknown>,
                            childReq,
                            arrayPath,
                            depth + 1,
                        ),
                    );
                }
            }
        }
    }
    return rows;
}

/**
 * 将 JSON Schema（常见 HTTP/LOCAL 子集）渲染为与 HTTP 工具一致的参数/字段表格。
 */
export function renderJsonSchemaPropertyTable(raw: unknown, role: SchemaTableRole): React.ReactNode {
    if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
        return (
            <Typography.Text type="secondary">
                {role === "input"
                    ? "未配置固定字段（运行时与「任意 JSON 对象」等价，由后端默认 schema 描述）"
                    : "未配置出参字段（不单独描述响应体结构）"}
            </Typography.Text>
        );
    }
    const schema = raw as Record<string, unknown>;
    /** 根级标量（常见于 LOCAL 纯文本工具出参），与「object + properties」区分 */
    const rootScalar = jsonSchemaTypeString(schema.type);
    if (
        rootScalar &&
        ["string", "number", "integer", "boolean"].includes(rootScalar) &&
        (schema.properties === undefined ||
            schema.properties === null ||
            (typeof schema.properties === "object" &&
                !Array.isArray(schema.properties) &&
                Object.keys(schema.properties as object).length === 0))
    ) {
        const desc = typeof schema.description === "string" ? schema.description : "";
        return (
            <Typography.Paragraph style={{marginBottom: 0}}>
                <Typography.Text code>{rootScalar}</Typography.Text>
                {desc ? ` — ${desc}` : ""}
            </Typography.Paragraph>
        );
    }

    if (!isHttpParameterSchemaFormEditable(raw)) {
        return (
            <Typography.Paragraph type="warning" style={{marginBottom: 0}}>
                {role === "input" ? (
                    <>
                        当前为<strong>高级入参 Schema</strong>（例如 <Typography.Text code>$ref</Typography.Text>
                        、组合关键字等），列表无法用表格逐行展示。完整内容见下方 definition 区域或复制 JSON。
                    </>
                ) : (
                    <>
                        当前为<strong>高级出参 Schema</strong>，无法用表格逐行展示。完整内容见下方 definition 或复制 JSON。
                    </>
                )}
            </Typography.Paragraph>
        );
    }
    const props = schema.properties;
    if (!props || typeof props !== "object" || Array.isArray(props)) {
        return (
            <Typography.Text type="secondary">
                无可表格化展示的 properties（可能仅有 additionalProperties 等）
            </Typography.Text>
        );
    }
    const propMap = props as Record<string, unknown>;
    const keys = Object.keys(propMap);
    if (keys.length === 0) {
        return <Typography.Text type="secondary">（properties 为空）</Typography.Text>;
    }
    const required = requiredArrayToSet(schema.required);
    const data = flattenSchemaPropertyRows(propMap, required, "", 0);
    const fieldTitle = role === "input" ? "参数路径" : "字段路径";
    const reqTitle = role === "input" ? "必填" : "必有";
    const columns: ColumnsType<NestedParamRow> = [
        {
            title: fieldTitle,
            dataIndex: "path",
            width: "30%",
            render: (_v, record) => (
                <span style={{display: "block", paddingLeft: record.depth * 12}}>
                    <Typography.Text code style={{fontSize: 12}}>
                        {record.path}
                    </Typography.Text>
                </span>
            ),
        },
        {
            title: "类型",
            dataIndex: "typ",
            width: "22%",
            render: (v) => (
                <Typography.Text code style={{fontSize: 12, wordBreak: "break-word"}}>
                    {v}
                </Typography.Text>
            ),
        },
        {
            title: reqTitle,
            dataIndex: "required",
            width: "10%",
            render: (v: boolean) => (v ? <Tag color="red">是</Tag> : <Tag>否</Tag>),
        },
        ...(role === "input"
            ? ([
                  {
                      title: "值来源",
                      dataIndex: "valueSourceLabel",
                      width: "12%",
                      render: (v: string) => (
                          <Typography.Text style={{fontSize: 12}}>{v}</Typography.Text>
                      ),
                  },
                  {
                      title: "固定值",
                      dataIndex: "fixedValueDisplay",
                      width: "14%",
                      render: (v: string) => (
                          <Typography.Text code style={{fontSize: 12, wordBreak: "break-word"}}>
                              {v}
                          </Typography.Text>
                      ),
                  },
              ] as ColumnsType<NestedParamRow>)
            : []),
        {
            title: "说明",
            dataIndex: "desc",
            render: (v) => <span style={{wordBreak: "break-word"}}>{v}</span>,
        },
    ];
    const hasArrayElementPaths = data.some((d) => d.path.includes("[]"));
    return (
        <>
            <Table<NestedParamRow>
                size="small"
                pagination={false}
                rowKey="rowKey"
                columns={columns}
                dataSource={data}
            />
            {hasArrayElementPaths ? (
                <Typography.Paragraph type="secondary" style={{marginBottom: 0, marginTop: 8, fontSize: 12}}>
                    路径中含 <Typography.Text code>[]</Typography.Text> 表示<strong>数组元素</strong>内的字段（例如{" "}
                    <Typography.Text code>items[].id</Typography.Text>）。
                </Typography.Paragraph>
            ) : null}
        </>
    );
}

export function renderHttpParametersTable(raw: unknown): React.ReactNode {
    return renderJsonSchemaPropertyTable(raw, "input");
}
