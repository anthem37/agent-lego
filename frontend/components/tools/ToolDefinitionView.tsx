"use client";

import {Button, Descriptions, message, Space, Table, Tag, Typography} from "antd";
import type {ColumnsType} from "antd/es/table";
import React from "react";

import {stringifyPretty} from "@/lib/json";
import {isHttpParameterSchemaFormEditable} from "@/lib/tools/form";

function ValueBlock(props: { value: unknown; depth?: number }) {
    const {value, depth = 0} = props;
    if (value === null || value === undefined) {
        return <Typography.Text type="secondary">—</Typography.Text>;
    }
    if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
        return (
            <Typography.Text code style={{wordBreak: "break-all"}}>
                {String(value)}
            </Typography.Text>
        );
    }
    if (Array.isArray(value)) {
        if (value.length === 0) {
            return <Typography.Text type="secondary">（空列表）</Typography.Text>;
        }
        if (depth >= 5) {
            return (
                <pre style={{margin: 0, fontSize: 12, whiteSpace: "pre-wrap"}}>{stringifyPretty(value)}</pre>
            );
        }
        return (
            <ul style={{margin: 0, paddingLeft: 20}}>
                {value.map((item, i) => (
                    <li key={i}>
                        <ValueBlock value={item} depth={depth + 1}/>
                    </li>
                ))}
            </ul>
        );
    }
    if (typeof value === "object") {
        const entries = Object.entries(value as Record<string, unknown>);
        if (entries.length === 0) {
            return <Typography.Text type="secondary">（空对象）</Typography.Text>;
        }
        if (depth >= 5) {
            return (
                <pre style={{margin: 0, fontSize: 12, whiteSpace: "pre-wrap"}}>{stringifyPretty(value)}</pre>
            );
        }
        return (
            <Descriptions bordered size="small" column={1} style={{marginTop: 0}}>
                {entries.map(([k, v]) => (
                    <Descriptions.Item key={k} label={k}>
                        <ValueBlock value={v} depth={depth + 1}/>
                    </Descriptions.Item>
                ))}
            </Descriptions>
        );
    }
    return <Typography.Text code>{String(value)}</Typography.Text>;
}

type HeaderRow = { name: string; value: string };

type ParamRow = { name: string; typ: string; required: boolean; desc: string };

function headersTable(headers: unknown): React.ReactNode {
    if (!headers || typeof headers !== "object" || Array.isArray(headers)) {
        return <Typography.Text type="secondary">—</Typography.Text>;
    }
    const entries = Object.entries(headers as Record<string, unknown>);
    if (entries.length === 0) {
        return <Typography.Text type="secondary">（未配置）</Typography.Text>;
    }
    const data: HeaderRow[] = entries.map(([name, v]) => ({
        name,
        value: v == null ? "" : typeof v === "object" ? stringifyPretty(v) : String(v),
    }));
    const columns: ColumnsType<HeaderRow> = [
        {title: "名称", dataIndex: "name", width: "35%", render: (v) => <Typography.Text code>{v}</Typography.Text>},
        {title: "取值", dataIndex: "value", render: (v) => <span style={{wordBreak: "break-all"}}>{v}</span>},
    ];
    return <Table<HeaderRow> size="small" pagination={false} rowKey="name" columns={columns} dataSource={data}/>;
}

type SchemaTableRole = "input" | "output";

function schemaPropertiesTable(raw: unknown, role: SchemaTableRole): React.ReactNode {
    if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
        return (
            <Typography.Text type="secondary">
                {role === "input"
                    ? "未配置固定字段（运行时与「任意 JSON 对象」等价，由后端默认 schema 描述）"
                    : "未配置出参字段（不单独描述响应体结构）"}
            </Typography.Text>
        );
    }
    if (!isHttpParameterSchemaFormEditable(raw)) {
        return (
            <Typography.Paragraph type="warning" style={{marginBottom: 0}}>
                {role === "input" ? (
                    <>
                        当前为<strong>高级入参 Schema</strong>（例如 <Typography.Text code>$ref</Typography.Text>
                        、组合关键字等），列表无法用表格逐行展示。完整内容见下方 definition 区域或复制 JSON；在编辑工具时修改 URL/请求头
                        <strong>不会覆盖</strong>该段配置。
                    </>
                ) : (
                    <>
                        当前为<strong>高级出参 Schema</strong>，无法用表格逐行展示。编辑时若处于「高级出参已保留」模式，保存不会覆盖{" "}
                        <Typography.Text code>outputSchema</Typography.Text>。
                    </>
                )}
            </Typography.Paragraph>
        );
    }
    const schema = raw as Record<string, unknown>;
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
    const required = Array.isArray(schema.required)
        ? new Set((schema.required as unknown[]).map((x) => String(x)))
        : new Set<string>();
    const data: ParamRow[] = keys.map((name) => {
        const spec = propMap[name];
        const s = spec && typeof spec === "object" && !Array.isArray(spec) ? (spec as Record<string, unknown>) : {};
        const typ = typeof s.type === "string" ? s.type : "—";
        const desc = typeof s.description === "string" ? s.description : "—";
        return {
            name,
            typ,
            required: required.has(name),
            desc,
        };
    });
    const fieldTitle = role === "input" ? "参数名" : "出参字段";
    const reqTitle = role === "input" ? "必填" : "必有";
    const columns: ColumnsType<ParamRow> = [
        {title: fieldTitle, dataIndex: "name", width: "22%", render: (v) => <Typography.Text code>{v}</Typography.Text>},
        {title: "类型", dataIndex: "typ", width: "14%"},
        {
            title: reqTitle,
            dataIndex: "required",
            width: "10%",
            render: (v: boolean) => (v ? <Tag color="red">是</Tag> : <Tag>否</Tag>),
        },
        {title: "说明", dataIndex: "desc", render: (v) => <span style={{wordBreak: "break-word"}}>{v}</span>},
    ];
    return <Table<ParamRow> size="small" pagination={false} rowKey="name" columns={columns} dataSource={data}/>;
}

function httpParametersTable(raw: unknown): React.ReactNode {
    return schemaPropertiesTable(raw, "input");
}

/**
 * 工具 definition 可读展示（避免整页只有一段 JSON）。
 */
export function ToolDefinitionView(props: {
    toolType: string;
    definition?: Record<string, unknown> | null;
}) {
    const def = props.definition ?? {};
    const t = (props.toolType ?? "").toUpperCase();
    const used = new Set<string>();
    const primaryItems: React.ReactNode[] = [];

    const take = (key: string) => {
        used.add(key);
        return def[key];
    };

    if (t === "HTTP") {
        const url = take("url");
        const method = take("method");
        const description = take("description");
        const sendJsonBody = take("sendJsonBody");
        if (def.parameters !== undefined) {
            used.add("parameters");
        }
        if (def.inputSchema !== undefined) {
            used.add("inputSchema");
        }
        const paramSchema = def.parameters ?? def.inputSchema;
        const outputSchema = take("outputSchema");
        const headers = take("headers");

        primaryItems.push(
            <Descriptions.Item key="url" label="请求 URL">
                {typeof url === "string" ? (
                    <Typography.Text code copyable style={{wordBreak: "break-all"}}>
                        {url}
                    </Typography.Text>
                ) : (
                    <ValueBlock value={url}/>
                )}
            </Descriptions.Item>,
            <Descriptions.Item key="method" label="HTTP 方法">
                {method != null ? <Tag color="green">{String(method).toUpperCase()}</Tag> : "—"}
            </Descriptions.Item>,
        );
        const methodUpper = String(method ?? "GET").toUpperCase();
        const httpMethodUsesJsonBody = ["POST", "PUT", "PATCH"].includes(methodUpper);
        if (httpMethodUsesJsonBody) {
            const sends = sendJsonBody !== false;
            primaryItems.push(
                <Descriptions.Item key="sendJsonBody" label="发送 JSON 请求体">
                    <Space size={8} wrap>
                        {sends ? <Tag color="blue">是</Tag> : <Tag>否</Tag>}
                        {sendJsonBody === undefined ? (
                            <Typography.Text type="secondary" style={{fontSize: 12}}>
                                旧数据未存该字段时与「是」一致
                            </Typography.Text>
                        ) : null}
                    </Space>
                </Descriptions.Item>,
            );
        }
        if (description !== undefined) {
            primaryItems.push(
                <Descriptions.Item key="description" label="说明">
                    {typeof description === "string" ? description : <ValueBlock value={description}/>}
                </Descriptions.Item>,
            );
        }
        primaryItems.push(
            <Descriptions.Item key="parameters" label="模型入参（parameters）" span={1}>
                {httpParametersTable(paramSchema)}
            </Descriptions.Item>,
        );
        primaryItems.push(
            <Descriptions.Item key="outputSchema" label="返回体（outputSchema）" span={1}>
                {schemaPropertiesTable(outputSchema, "output")}
            </Descriptions.Item>,
        );
        primaryItems.push(
            <Descriptions.Item key="headers" label="请求头" span={1}>
                {headersTable(headers)}
            </Descriptions.Item>,
        );
    } else if (t === "WORKFLOW") {
        const workflowId = take("workflowId");
        const description = take("description");
        primaryItems.push(
            <Descriptions.Item key="workflowId" label="工作流 ID">
                {workflowId != null ? (
                    <Typography.Text code copyable>
                        {String(workflowId)}
                    </Typography.Text>
                ) : (
                    "—"
                )}
            </Descriptions.Item>,
        );
        if (description !== undefined) {
            primaryItems.push(
                <Descriptions.Item key="description" label="说明">
                    {typeof description === "string" ? description : <ValueBlock value={description}/>}
                </Descriptions.Item>,
            );
        }
    } else if (t === "MCP") {
        const endpoint = take("endpoint");
        const mcpToolName = take("mcpToolName");
        const description = take("description");
        if (def.parameters !== undefined) {
            used.add("parameters");
        }
        if (def.inputSchema !== undefined) {
            used.add("inputSchema");
        }
        const paramSchema = def.parameters ?? def.inputSchema;
        primaryItems.push(
            <Descriptions.Item key="endpoint" label="远端 SSE 端点">
                {endpoint != null && endpoint !== "" ? (
                    typeof endpoint === "string" ? (
                        <Typography.Text code copyable style={{wordBreak: "break-all"}}>
                            {endpoint}
                        </Typography.Text>
                    ) : (
                        <ValueBlock value={endpoint}/>
                    )
                ) : (
                    <Typography.Text type="warning">未配置</Typography.Text>
                )}
            </Descriptions.Item>,
        );
        primaryItems.push(
            <Descriptions.Item key="mcpToolName" label="远端工具名（tools/call）">
                {mcpToolName != null && String(mcpToolName).trim() !== "" ? (
                    typeof mcpToolName === "string" ? (
                        <Typography.Text code copyable>
                            {mcpToolName}
                        </Typography.Text>
                    ) : (
                        <ValueBlock value={mcpToolName}/>
                    )
                ) : (
                    <Typography.Text type="secondary">与平台工具 name 一致（未单独配置 mcpToolName）</Typography.Text>
                )}
            </Descriptions.Item>,
        );
        if (description !== undefined) {
            primaryItems.push(
                <Descriptions.Item key="description" label="说明">
                    {typeof description === "string" ? description : <ValueBlock value={description}/>}
                </Descriptions.Item>,
            );
        }
        primaryItems.push(
            <Descriptions.Item key="mcp-parameters" label="调用入参（parameters / inputSchema）" span={1}>
                {httpParametersTable(paramSchema)}
            </Descriptions.Item>,
        );
    } else {
        // LOCAL 及其他
        const description = take("description");
        if (description !== undefined) {
            primaryItems.push(
                <Descriptions.Item key="description" label="说明">
                    {typeof description === "string" ? description : <ValueBlock value={description}/>}
                </Descriptions.Item>,
            );
        }
    }

    const restKeys = Object.keys(def).filter((k) => !used.has(k));
    const jsonText = stringifyPretty(def);
    const canCopy = jsonText.length > 0 && jsonText !== "{}";

    return (
        <Space direction="vertical" size="middle" style={{width: "100%"}}>
            {Object.keys(def).length === 0 ? (
                <Typography.Text type="secondary">无 definition 配置</Typography.Text>
            ) : (
                <>
                    {primaryItems.length > 0 ? (
                        <Descriptions bordered size="small" column={1}>
                            {primaryItems}
                        </Descriptions>
                    ) : null}
                    {restKeys.length > 0 ? (
                        <>
                            <Typography.Text strong>其他字段</Typography.Text>
                            <Typography.Paragraph type="secondary" style={{marginBottom: 8}}>
                                如下为表单未单独拆行的配置（例如 inputSchema 等），以结构化方式展示。
                            </Typography.Paragraph>
                            <Descriptions bordered size="small" column={1}>
                                {restKeys.map((k) => (
                                    <Descriptions.Item key={k} label={k}>
                                        <ValueBlock value={def[k]}/>
                                    </Descriptions.Item>
                                ))}
                            </Descriptions>
                        </>
                    ) : null}
                </>
            )}
            {canCopy ? (
                <Button
                    size="small"
                    type="link"
                    style={{padding: 0, height: "auto"}}
                    onClick={() => {
                        void navigator.clipboard.writeText(jsonText).then(
                            () => message.success("已复制到剪贴板"),
                            () => message.error("复制失败，请手动选择文本"),
                        );
                    }}
                >
                    复制完整 JSON（便于对接或排错）
                </Button>
            ) : null}
        </Space>
    );
}
