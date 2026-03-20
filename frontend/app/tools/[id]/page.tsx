"use client";

import {EditOutlined, MinusCircleOutlined, PlusOutlined} from "@ant-design/icons";
import {Button, Descriptions, Form, Input, message, Space, Spin, Tabs, Tag, Typography} from "antd";
import Link from "next/link";
import {useRouter} from "next/navigation";
import React from "react";

import {DeleteToolPopconfirm} from "@/components/tools/DeleteToolPopconfirm";
import {ToolDefinitionView} from "@/components/tools/ToolDefinitionView";
import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {LocalBuiltinIoPreview} from "@/components/tools/LocalBuiltinIoPreview";
import {ToolFormDrawer} from "@/components/tools/ToolFormDrawer";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {SectionCard} from "@/components/SectionCard";
import {request} from "@/lib/api/request";
import {
    deleteTool,
    fetchLocalBuiltinToolsMeta,
    fetchToolReferences,
    fetchToolTypeMeta,
    getTool,
} from "@/lib/tools/api";
import {stringifyPretty} from "@/lib/json";
import {shouldPreserveHttpOutputFields, shouldPreserveHttpParameterFields} from "@/lib/tools/form";
import {buildDefaultTestCallParamRows} from "@/lib/tools/test-call";
import {toolTypeDisplayName} from "@/lib/tool-labels";
import {toolTypeTagColor} from "@/lib/tools/ui";
import type {LocalBuiltinToolMetaDto, ToolDto, ToolReferencesDto, ToolTypeMetaDto} from "@/lib/tools/types";

type TestToolCallForm = {
    /** 扁平入参：名称 + 取值，无需整段 JSON */
    paramRows?: { paramName: string; paramValue: string }[];
};

function buildTestInputFromRows(rows: TestToolCallForm["paramRows"]): Record<string, unknown> {
    const out: Record<string, unknown> = {};
    for (const row of rows ?? []) {
        const k = (row.paramName ?? "").trim();
        if (!k) {
            continue;
        }
        const raw = (row.paramValue ?? "").trim();
        if (raw === "") {
            out[k] = "";
            continue;
        }
        if (
            (raw.startsWith("{") && raw.endsWith("}")) ||
            (raw.startsWith("[") && raw.endsWith("]"))
        ) {
            try {
                out[k] = JSON.parse(raw) as unknown;
                continue;
            } catch {
                // 非法 JSON 时按普通字符串
            }
        }
        out[k] = raw;
    }
    return out;
}

type TestToolCallView = {
    output: string;
    raw: string;
};

function summarizeToolResult(result: unknown): string {
    if (result == null) {
        return "—";
    }
    if (typeof result === "string") {
        return result;
    }
    if (typeof result === "object") {
        const o = result as Record<string, unknown>;
        const output = o.output;
        if (Array.isArray(output)) {
            const texts = output
                .map((block) => {
                    if (block && typeof block === "object" && "text" in block) {
                        return String((block as { text?: unknown }).text ?? "");
                    }
                    return "";
                })
                .filter(Boolean);
            if (texts.length > 0) {
                return texts.join("\n");
            }
        }
    }
    return stringifyPretty(result);
}

type TestToolCallApiResponse = {
    result?: unknown;
};

export default function ToolDetailPage(props: { params: Promise<{ id: string }> }) {
    const router = useRouter();
    const [toolId, setToolId] = React.useState<string | null>(null);
    const [tool, setTool] = React.useState<ToolDto | null>(null);
    const [meta, setMeta] = React.useState<ToolTypeMetaDto[]>([]);
    const [localBuiltins, setLocalBuiltins] = React.useState<LocalBuiltinToolMetaDto[]>([]);
    const [loading, setLoading] = React.useState(false);
    const [testing, setTesting] = React.useState(false);
    const [testOut, setTestOut] = React.useState<TestToolCallView | null>(null);
    const [error, setError] = React.useState<unknown>(null);
    const [tab, setTab] = React.useState<string>("overview");
    const [editOpen, setEditOpen] = React.useState(false);
    const [deleting, setDeleting] = React.useState(false);
    const [refs, setRefs] = React.useState<ToolReferencesDto | null>(null);
    const [form] = Form.useForm<TestToolCallForm>();

    const typeMeta = React.useMemo(() => {
        if (!tool) {
            return undefined;
        }
        return meta.find((m) => m.code.toUpperCase() === tool.toolType.toUpperCase());
    }, [meta, tool]);

    const localBuiltinMeta = React.useMemo(() => {
        if (!tool || tool.toolType.toUpperCase() !== "LOCAL") {
            return undefined;
        }
        return localBuiltins.find((b) => b.name === tool.name);
    }, [tool, localBuiltins]);

    const mcpOverview = React.useMemo(() => {
        if (!tool || tool.toolType.toUpperCase() !== "MCP") {
            return null;
        }
        const d = tool.definition ?? {};
        const endpoint = typeof d.endpoint === "string" ? d.endpoint : "";
        const explicitRemote =
            typeof d.mcpToolName === "string" && d.mcpToolName.trim() ? d.mcpToolName.trim() : null;
        const effectiveRemote = explicitRemote ?? tool.name;
        const origin = typeof window !== "undefined" ? window.location.origin : "";
        return {endpoint, explicitRemote, effectiveRemote, origin};
    }, [tool]);

    const loadAll = React.useCallback(
        async (id: string) => {
            setError(null);
            setLoading(true);
            try {
                const [data, m, r, builtins] = await Promise.all([
                    getTool(id),
                    fetchToolTypeMeta(),
                    fetchToolReferences(id),
                    fetchLocalBuiltinToolsMeta(),
                ]);
                setTool(data);
                setMeta(m);
                setRefs(r);
                setLocalBuiltins(builtins);
                const localMeta =
                    data.toolType.toUpperCase() === "LOCAL"
                        ? builtins.find((b) => b.name === data.name)
                        : undefined;
                form.setFieldsValue({
                    paramRows: buildDefaultTestCallParamRows(data, {localBuiltinMeta: localMeta}),
                });
            } catch (e) {
                setError(e);
                setTool(null);
                setRefs(null);
            } finally {
                setLoading(false);
            }
        },
        [form],
    );

    React.useEffect(() => {
        let cancelled = false;
        void props.params.then(({id}) => {
            if (!cancelled) {
                setToolId(id);
                void loadAll(id);
            }
        });
        return () => {
            cancelled = true;
        };
    }, [props.params, loadAll]);

    async function onTest(values: TestToolCallForm) {
        if (!tool) {
            return;
        }
        setError(null);
        setTesting(true);
        setTestOut(null);
        try {
            const input = buildTestInputFromRows(values.paramRows);
            const data = await request<TestToolCallApiResponse>(`/tools/${tool.id}/test-call`, {
                method: "POST",
                body: {input},
            });
            setTestOut({
                output: summarizeToolResult(data?.result),
                raw: stringifyPretty(data?.result ?? data),
            });
            message.success("调用完成");
        } catch (e) {
            setError(e);
        } finally {
            setTesting(false);
        }
    }

    async function onDelete() {
        if (!tool) {
            return;
        }
        setDeleting(true);
        setError(null);
        try {
            await deleteTool(tool.id);
            message.success("已删除");
            router.push("/tools");
        } catch (e) {
            setError(e);
        } finally {
            setDeleting(false);
        }
    }

    const overviewTab = tool ? (
        <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="工具名称">{tool.name}</Descriptions.Item>
            <Descriptions.Item label="AgentScope">
                <Typography.Text type="secondary" style={{fontSize: 13}}>
                    运行时以此名称注册到 Toolkit，与模型 function calling 一致；平台内全平台唯一（大小写不敏感）。
                </Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label="类型">
                <Typography.Text>
                    <Typography.Text code style={{marginRight: 8}}>
                        {tool.toolType}
                    </Typography.Text>
                    <Typography.Text type="secondary">{toolTypeDisplayName(tool.toolType)}</Typography.Text>
                </Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label="联调能力">
                {typeMeta?.supportsTestCall === false ? (
                    <Typography.Text type="warning">当前工具类型不支持 test-call</Typography.Text>
                ) : (
                    <Typography.Text type="success">可在本页「联调」发起 test-call</Typography.Text>
                )}
            </Descriptions.Item>
            <Descriptions.Item label="智能体引用">
                {refs == null ? (
                    "—"
                ) : refs.referencingAgentCount === 0 ? (
                    <Typography.Text type="success">未被任何智能体 toolIds 引用</Typography.Text>
                ) : (
                    <Space direction="vertical" size={4} style={{width: "100%"}}>
                        <Typography.Text type="warning">
                            {refs.referencingAgentCount} 个智能体仍在使用此工具 ID
                        </Typography.Text>
                        {refs.referencingAgentIds.length > 0 ? (
                            <Typography.Paragraph style={{marginBottom: 0}}
                                                  copyable={{text: refs.referencingAgentIds.join("\n")}}>
                                <Typography.Text code style={{fontSize: 12}}>
                                    {refs.referencingAgentIds.join(", ")}
                                </Typography.Text>
                            </Typography.Paragraph>
                        ) : null}
                        <Link href="/agents">去智能体管理调整 toolIds →</Link>
                    </Space>
                )}
            </Descriptions.Item>
            {typeMeta?.description ? (
                <Descriptions.Item label="类型说明">{typeMeta.description}</Descriptions.Item>
            ) : null}
            {localBuiltinMeta ? (
                <Descriptions.Item label="内置入参 / 出参">
                    <LocalBuiltinIoPreview meta={localBuiltinMeta} showTitle={false}/>
                </Descriptions.Item>
            ) : null}
            {mcpOverview ? (
                <Descriptions.Item label="MCP 配置">
                    <Space orientation="vertical" size={8} style={{width: "100%"}}>
                        <div>
                            <Typography.Text type="secondary" style={{fontSize: 12}}>
                                远端 SSE 端点
                            </Typography.Text>
                            <div>
                                {mcpOverview.endpoint ? (
                                    <Typography.Text code copyable style={{wordBreak: "break-all"}}>
                                        {mcpOverview.endpoint}
                                    </Typography.Text>
                                ) : (
                                    <Typography.Text type="warning">未配置 endpoint</Typography.Text>
                                )}
                            </div>
                        </div>
                        <div>
                            <Typography.Text type="secondary" style={{fontSize: 12}}>
                                tools/call 使用的工具名
                            </Typography.Text>
                            <div>
                                <Typography.Text code>{mcpOverview.effectiveRemote}</Typography.Text>
                                {mcpOverview.explicitRemote ? null : (
                                    <Typography.Text type="secondary" style={{marginLeft: 8}}>
                                        （与平台名称相同，未单独配置 mcpToolName）
                                    </Typography.Text>
                                )}
                            </div>
                        </div>
                        {mcpOverview.origin ? (
                            <Typography.Paragraph type="secondary" style={{marginBottom: 0, fontSize: 13}}>
                                本服务作为 MCP Server 时，客户端可连接：{" "}
                                <Typography.Text code>
                                    {mcpOverview.origin}/mcp
                                </Typography.Text>{" "}
                                （可在后端修改 <Typography.Text code>agentlego.mcp.server.sse-path</Typography.Text>）
                            </Typography.Paragraph>
                        ) : null}
                    </Space>
                </Descriptions.Item>
            ) : null}
            <Descriptions.Item label="创建时间">{tool.createdAt ?? "—"}</Descriptions.Item>
            <Descriptions.Item label="工具 ID（智能体 toolIds）">
                <Typography.Text code copyable>
                    {tool.id}
                </Typography.Text>
            </Descriptions.Item>
            {tool.toolType.toUpperCase() === "HTTP" &&
            (shouldPreserveHttpParameterFields(tool.definition ?? {}) ||
                shouldPreserveHttpOutputFields(tool.definition ?? {})) ? (
                <Descriptions.Item label="HTTP Schema 编辑">
                    <Space direction="vertical" size={8}>
                        {shouldPreserveHttpParameterFields(tool.definition ?? {}) ? (
                            <div>
                                <Tag color="orange">入参高级 Schema 已保留</Tag>
                                <Typography.Text type="secondary"
                                                 style={{fontSize: 13, display: "block", marginTop: 4}}>
                                    parameters / inputSchema 无法用表格无损表达时，保存
                                    URL/请求头等<strong>不会覆盖</strong>
                                    入参字段；可在编辑页「改为表单配置入参」切换。
                                </Typography.Text>
                            </div>
                        ) : null}
                        {shouldPreserveHttpOutputFields(tool.definition ?? {}) ? (
                            <div>
                                <Tag color="orange">出参高级 Schema 已保留</Tag>
                                <Typography.Text type="secondary"
                                                 style={{fontSize: 13, display: "block", marginTop: 4}}>
                                    outputSchema 无法用表格无损表达时，保存其他字段<strong>不会覆盖</strong>
                                    出参定义；可在编辑页「改为表单配置出参」切换。
                                </Typography.Text>
                            </div>
                        ) : null}
                    </Space>
                </Descriptions.Item>
            ) : null}
            <Descriptions.Item label="definition（可读）">
                <ToolDefinitionView toolType={tool.toolType} definition={tool.definition}/>
            </Descriptions.Item>
        </Descriptions>
    ) : null;

    const testTab = (
        <Space orientation="vertical" size={12} style={{width: "100%"}}>
            <Typography.Paragraph type="secondary" style={{marginBottom: 0}}>
                打开本页或刷新后，会根据工具类型<strong>自动预填参数名</strong>（LOCAL 来自内置契约，HTTP 来自
                parameters / URL 占位符，WORKFLOW 默认 <Typography.Text code>input</Typography.Text>
                ）。只需补全「取值」即可；若取值本身是 JSON 对象/数组，可直接粘贴到取值框。MCP 将请求发到已登记的远端 SSE
                Server。
            </Typography.Paragraph>
            <Form<TestToolCallForm> form={form} layout="vertical" onFinish={onTest}>
                <Form.Item label="调用入参（可选）">
                    <Form.List name="paramRows">
                        {(fields, {add, remove}) => (
                            <>
                                {fields.map(({key, name, ...restField}) => (
                                    <Space
                                        key={key}
                                        style={{display: "flex", marginBottom: 8}}
                                        align="baseline"
                                    >
                                        <Form.Item
                                            {...restField}
                                            name={[name, "paramName"]}
                                            style={{flex: 1, marginBottom: 0}}
                                        >
                                            <Input placeholder="参数名，如 content / input / city"/>
                                        </Form.Item>
                                        <Form.Item
                                            {...restField}
                                            name={[name, "paramValue"]}
                                            style={{flex: 1, marginBottom: 0}}
                                        >
                                            <Input placeholder="取值（可为空；可粘贴小段 JSON）"/>
                                        </Form.Item>
                                        <MinusCircleOutlined onClick={() => remove(name)}/>
                                    </Space>
                                ))}
                                <Form.Item style={{marginBottom: 0}}>
                                    <Button
                                        type="dashed"
                                        onClick={() => add({paramName: "", paramValue: ""})}
                                        block
                                        icon={<PlusOutlined/>}
                                    >
                                        添加参数
                                    </Button>
                                </Form.Item>
                            </>
                        )}
                    </Form.List>
                </Form.Item>
                <Form.Item>
                    <Button type="primary" htmlType="submit" loading={testing} disabled={!tool}>
                        发起 test-call
                    </Button>
                </Form.Item>
            </Form>
            {testOut ? (
                <Descriptions column={1} size="small" bordered title="返回">
                    <Descriptions.Item label="摘要（output）">
                        <pre style={{margin: 0, whiteSpace: "pre-wrap"}}>{testOut.output}</pre>
                    </Descriptions.Item>
                    <Descriptions.Item label="原始 JSON">
                        <pre style={{margin: 0, whiteSpace: "pre-wrap"}}>{testOut.raw}</pre>
                    </Descriptions.Item>
                </Descriptions>
            ) : (
                <Typography.Text type="secondary">尚未调用</Typography.Text>
            )}
        </Space>
    );

    return (
        <AppLayout>
            <Space orientation="vertical" size={16} style={{width: "100%"}}>
                <PageHeaderBlock
                    title={tool ? `工具：${tool.name}` : "工具详情"}
                    subtitle={tool ? `ID ${tool.id}` : toolId ? `加载 ${toolId} …` : "加载中…"}
                    extra={
                        <Space wrap>
                            {tool ? <Tag
                                color={toolTypeTagColor(tool.toolType)}>{toolTypeDisplayName(tool.toolType)}</Tag> : null}
                            <Link href="/tools">
                                <Button>返回列表</Button>
                            </Link>
                            {tool ? (
                                <>
                                    <Button icon={<EditOutlined/>} onClick={() => setEditOpen(true)}>
                                        编辑
                                    </Button>
                                    <DeleteToolPopconfirm
                                        toolId={tool.id}
                                        deleting={deleting}
                                        onConfirm={() => void onDelete()}
                                        trigger={<Button danger>删除</Button>}
                                    />
                                </>
                            ) : null}
                        </Space>
                    }
                />

                <ErrorAlert error={error}/>

                <SectionCard title="详情与联调">
                    <Spin spinning={loading}>
                        {tool ? (
                            <Tabs
                                activeKey={tab}
                                onChange={setTab}
                                items={[
                                    {key: "overview", label: "概览", children: overviewTab},
                                    {key: "test", label: "联调", children: testTab},
                                ]}
                            />
                        ) : (
                            <Typography.Text type="secondary">{loading ? "加载中…" : "未找到工具"}</Typography.Text>
                        )}
                    </Spin>
                </SectionCard>

                {tool ? (
                    <ToolFormDrawer
                        open={editOpen}
                        mode="edit"
                        editingTool={tool}
                        toolTypeMeta={meta}
                        localBuiltins={localBuiltins}
                        onClose={() => setEditOpen(false)}
                        onSaved={async () => {
                            if (toolId) {
                                await loadAll(toolId);
                            }
                        }}
                    />
                ) : null}
            </Space>
        </AppLayout>
    );
}
