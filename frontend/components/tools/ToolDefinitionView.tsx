"use client";

import {Button, Descriptions, message, Space, Table, Tag, Typography} from "antd";
import type {ColumnsType} from "antd/es/table";
import React from "react";

import {stringifyPretty} from "@/lib/json";
import {renderHttpParametersTable, renderJsonSchemaPropertyTable} from "@/components/tools/JsonSchemaPropertyTable";

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
        const parameterAliases = take("parameterAliases");
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
                {renderHttpParametersTable(paramSchema)}
            </Descriptions.Item>,
        );
        primaryItems.push(
            <Descriptions.Item key="outputSchema" label="返回体（outputSchema）" span={1}>
                {renderJsonSchemaPropertyTable(outputSchema, "output")}
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
        const outputSchema = take("outputSchema");
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
        primaryItems.push(
            <Descriptions.Item key="workflow-outputSchema" label="返回体（outputSchema）" span={1}>
                {renderJsonSchemaPropertyTable(outputSchema, "output")}
            </Descriptions.Item>,
        );
    } else if (t === "MCP") {
        const endpoint = take("endpoint");
        const mcpToolName = take("mcpToolName");
        const description = take("description");
        const outputSchema = take("outputSchema");
        if (def.parameters !== undefined) {
            used.add("parameters");
        }
        if (def.inputSchema !== undefined) {
            used.add("inputSchema");
        }
        const paramSchema = def.parameters ?? def.inputSchema;
        const parameterAliases = take("parameterAliases");
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
                {renderHttpParametersTable(paramSchema)}
            </Descriptions.Item>,
        );
        primaryItems.push(
            <Descriptions.Item key="mcp-parameterAliases" label="入参别名（parameterAliases）" span={1}>
                {parameterAliases != null &&
                typeof parameterAliases === "object" &&
                !Array.isArray(parameterAliases) &&
                Object.keys(parameterAliases as Record<string, unknown>).length > 0 ? (
                    <ValueBlock value={parameterAliases}/>
                ) : (
                    <Typography.Text type="secondary">（未配置）</Typography.Text>
                )}
            </Descriptions.Item>,
        );
        primaryItems.push(
            <Descriptions.Item key="mcp-outputSchema" label="返回体（outputSchema）" span={1}>
                {renderJsonSchemaPropertyTable(outputSchema, "output")}
            </Descriptions.Item>,
        );
    } else if (t === "LOCAL") {
        const description = take("description");
        if (def.inputSchema !== undefined) {
            used.add("inputSchema");
        }
        if (def.outputSchema !== undefined) {
            used.add("outputSchema");
        }
        const inputSchema = take("inputSchema");
        const outputSchema = take("outputSchema");
        if (description !== undefined) {
            primaryItems.push(
                <Descriptions.Item key="description" label="说明">
                    {typeof description === "string" ? description : <ValueBlock value={description}/>}
                </Descriptions.Item>,
            );
        }
        primaryItems.push(
            <Descriptions.Item key="local-inputSchema" label="模型入参（inputSchema）" span={1}>
                {renderJsonSchemaPropertyTable(inputSchema, "input")}
            </Descriptions.Item>,
        );
        primaryItems.push(
            <Descriptions.Item key="local-outputSchema" label="返回体（outputSchema）" span={1}>
                {renderJsonSchemaPropertyTable(outputSchema, "output")}
            </Descriptions.Item>,
        );
    } else {
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
        <Space orientation="vertical" size="middle" style={{width: "100%"}}>
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
