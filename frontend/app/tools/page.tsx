"use client";

import {
    Alert,
    Button,
    Collapse,
    Drawer,
    Form,
    Input,
    message,
    Select,
    Segmented,
    Space,
    Table,
    Tag,
    Tooltip,
    Typography,
} from "antd";
import type {ColumnsType} from "antd/es/table";
import Link from "next/link";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {JsonTextArea} from "@/components/JsonTextArea";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {SectionCard} from "@/components/SectionCard";
import {request} from "@/lib/api/request";
import {parseJsonObject, stringifyPretty} from "@/lib/json";
import {tablePaginationFriendly} from "@/lib/table-pagination";
import {toolTypeDisplayName} from "@/lib/tool-labels";

type ToolDto = {
    id: string;
    toolType: string;
    name: string;
    definition?: Record<string, unknown>;
    createdAt?: string;
};

type CreateToolForm = {
    toolType: "LOCAL" | "MCP";
    name: string;
    toolDescription?: string;
    mcpEndpoint?: string;
    definitionExtraJson?: string;
};

const LOCAL_BUILTIN_OPTIONS = [
    {
        value: "echo",
        label: "echo — 回显文本",
        hint: "联调时参数示例：`{\"content\":\"你好\"}`",
    },
    {
        value: "now",
        label: "now — 当前时间",
        hint: "联调时参数可为 `{}` 或留空。",
    },
] as const;

function buildToolDefinition(values: CreateToolForm): Record<string, unknown> | undefined {
    const def: Record<string, unknown> = {};
    if (values.toolDescription?.trim()) {
        def.description = values.toolDescription.trim();
    }
    if (values.toolType === "MCP" && values.mcpEndpoint?.trim()) {
        def.endpoint = values.mcpEndpoint.trim();
    }
    if (values.definitionExtraJson?.trim()) {
        Object.assign(def, parseJsonObject(values.definitionExtraJson));
    }
    return Object.keys(def).length > 0 ? def : undefined;
}

function toolTypeTagColor(t: string): string {
    const c = t.toUpperCase();
    if (c === "LOCAL") {
        return "blue";
    }
    if (c === "MCP") {
        return "geekblue";
    }
    return "default";
}

export default function ToolsPage() {
    const [tools, setTools] = React.useState<ToolDto[]>([]);
    const [loading, setLoading] = React.useState(false);
    const [drawerOpen, setDrawerOpen] = React.useState(false);
    const [creating, setCreating] = React.useState(false);
    const [error, setError] = React.useState<unknown>(null);
    const [keyword, setKeyword] = React.useState("");
    const [form] = Form.useForm<CreateToolForm>();
    const watchedToolType = Form.useWatch("toolType", form) ?? "LOCAL";

    async function reload() {
        setError(null);
        setLoading(true);
        try {
            const list = await request<ToolDto[]>("/tools");
            setTools(Array.isArray(list) ? list : []);
        } catch (e) {
            setError(e);
        } finally {
            setLoading(false);
        }
    }

    React.useEffect(() => {
        void reload();
    }, []);

    function openCreate() {
        form.resetFields();
        form.setFieldsValue({
            toolType: "LOCAL",
            name: "echo",
        });
        setError(null);
        setDrawerOpen(true);
    }

    async function onCreate(values: CreateToolForm) {
        setError(null);
        setCreating(true);
        try {
            const definition = buildToolDefinition(values);
            await request<string>("/tools", {
                method: "POST",
                body: {
                    toolType: values.toolType,
                    name: values.name.trim(),
                    definition,
                },
            });
            message.success("工具已创建");
            setDrawerOpen(false);
            await reload();
        } catch (e) {
            setError(e);
        } finally {
            setCreating(false);
        }
    }

    const filtered = React.useMemo(() => {
        const k = keyword.trim().toLowerCase();
        if (!k) {
            return tools;
        }
        return tools.filter((r) => {
            const defStr = r.definition ? stringifyPretty(r.definition).toLowerCase() : "";
            return (
                r.id.toLowerCase().includes(k) ||
                r.name.toLowerCase().includes(k) ||
                r.toolType.toLowerCase().includes(k) ||
                toolTypeDisplayName(r.toolType).toLowerCase().includes(k) ||
                defStr.includes(k)
            );
        });
    }, [tools, keyword]);

    const columns: ColumnsType<ToolDto> = [
        {
            title: "工具名称",
            dataIndex: "name",
            width: 200,
            ellipsis: true,
            render: (v: string, record) => (
                <Tooltip title={v}>
                    <Link href={`/tools/${record.id}`}>
                        <Typography.Text strong ellipsis style={{maxWidth: 180}}>
                            {v}
                        </Typography.Text>
                    </Link>
                </Tooltip>
            ),
        },
        {
            title: "类型",
            dataIndex: "toolType",
            width: 140,
            render: (v: string) => (
                <Tag color={toolTypeTagColor(v)}>{toolTypeDisplayName(v)}</Tag>
            ),
        },
        {
            title: "编号",
            dataIndex: "id",
            width: 200,
            ellipsis: true,
            render: (v: string) => (
                <Typography.Text code copyable={{text: v}} ellipsis style={{maxWidth: 180}}>
                    {v}
                </Typography.Text>
            ),
        },
        {
            title: "创建时间",
            dataIndex: "createdAt",
            width: 190,
            render: (v: string | undefined) => v ?? "—",
        },
        {
            title: "操作",
            key: "actions",
            width: 120,
            fixed: "right",
            render: (_, record) => <Link href={`/tools/${record.id}`}>详情与联调</Link>,
        },
    ];

    return (
        <AppLayout>
            <Space orientation="vertical" size={16} style={{width: "100%"}}>
                <PageHeaderBlock
                    title="工具管理"
                    subtitle="注册本地或 MCP 工具；本地内置 echo / now 可在详情页 test-call。MCP 可先登记配置，运行与联调能力将逐步接入。列表支持按名称、类型、编号、定义内容搜索。"
                    extra={
                        <Space wrap>
                            <Button onClick={() => void reload()} loading={loading}>
                                刷新列表
                            </Button>
                            <Button type="primary" onClick={openCreate}>
                                新建工具
                            </Button>
                        </Space>
                    }
                />

                <ErrorAlert error={error}/>

                <SectionCard title="工具列表">
                    <Space orientation="vertical" size={12} style={{width: "100%"}}>
                        <Space wrap style={{width: "100%", justifyContent: "space-between"}}>
                            <Input.Search
                                allowClear
                                placeholder="搜索名称、类型、编号或定义 JSON 片段…"
                                style={{width: 360}}
                                onSearch={setKeyword}
                                onChange={(e) => setKeyword(e.target.value)}
                            />
                            <Typography.Text type="secondary">共 {filtered.length} 条</Typography.Text>
                        </Space>
                        <Table<ToolDto>
                            rowKey="id"
                            loading={loading}
                            dataSource={filtered}
                            columns={columns}
                            scroll={{x: 900}}
                            pagination={tablePaginationFriendly()}
                        />
                    </Space>
                </SectionCard>

                <Drawer
                    title="新建工具"
                    width={620}
                    open={drawerOpen}
                    onClose={() => setDrawerOpen(false)}
                    destroyOnClose
                    extra={
                        <Space>
                            <Button onClick={() => setDrawerOpen(false)}>取消</Button>
                            <Button type="primary" loading={creating} onClick={() => void form.submit()}>
                                创建
                            </Button>
                        </Space>
                    }
                >
                    <Typography.Paragraph type="secondary" style={{marginTop: 0}}>
                        同一类型下工具名称需唯一。多数场景用下方表单即可；只有需要合并额外字段时再展开「高级 JSON」。
                    </Typography.Paragraph>
                    <Form
                        form={form}
                        layout="vertical"
                        initialValues={{toolType: "LOCAL", name: "echo"}}
                        onFinish={(v) => void onCreate(v as CreateToolForm)}
                        onValuesChange={(changed) => {
                            if (changed.toolType === "LOCAL") {
                                form.setFieldsValue({
                                    name: "echo",
                                    mcpEndpoint: undefined,
                                });
                            }
                            if (changed.toolType === "MCP") {
                                form.setFieldsValue({name: ""});
                            }
                        }}
                    >
                        <Form.Item
                            name="toolType"
                            label="你要添加哪类工具？"
                            rules={[{required: true, message: "请选择工具类型"}]}
                        >
                            <Segmented
                                block
                                options={[
                                    {label: "本地内置", value: "LOCAL"},
                                    {label: "MCP 外部", value: "MCP"},
                                ]}
                            />
                        </Form.Item>

                        {watchedToolType === "LOCAL" ? (
                            <>
                                <Alert
                                    type="info"
                                    showIcon
                                    message="当前运行环境仅实现 echo 与 now"
                                    description="请选择下方内置工具名称；若填写其它名称，注册可能成功，但执行时会报不支持。"
                                    style={{marginBottom: 16}}
                                />
                                <Form.Item
                                    name="name"
                                    label="内置工具"
                                    rules={[{required: true, message: "请选择内置工具"}]}
                                    tooltip="必须与后端实现一致，才能在详情页成功 test-call。"
                                >
                                    <Select
                                        options={LOCAL_BUILTIN_OPTIONS.map((o) => ({
                                            value: o.value,
                                            label: o.label,
                                        }))}
                                    />
                                </Form.Item>
                                <Form.Item shouldUpdate noStyle>
                                    {() => {
                                        const n = form.getFieldValue("name") as string | undefined;
                                        const opt = LOCAL_BUILTIN_OPTIONS.find((o) => o.value === n);
                                        return opt ? (
                                            <Typography.Paragraph type="secondary" style={{marginTop: -8}}>
                                                {opt.hint}
                                            </Typography.Paragraph>
                                        ) : null;
                                    }}
                                </Form.Item>
                            </>
                        ) : (
                            <>
                                <Alert
                                    type="warning"
                                    showIcon
                                    message="MCP：可先登记，联调/执行能力未完全接入"
                                    description="保存后可在列表查看；详情页 test-call 等能力可能返回未实现，请以后续版本说明为准。"
                                    style={{marginBottom: 16}}
                                />
                                <Form.Item
                                    name="name"
                                    label="平台上的工具名称（name）"
                                    rules={[
                                        {required: true, message: "请输入工具名称"},
                                        {
                                            pattern: /^[a-zA-Z][a-zA-Z0-9_-]*$/,
                                            message: "建议使用英文标识：字母开头，可含数字、下划线、短横线",
                                        },
                                    ]}
                                    tooltip="在智能体 toolIds 中引用的是此名称（与类型共同唯一）。"
                                >
                                    <Input placeholder="例如 search_docs、read_file"/>
                                </Form.Item>
                                <Form.Item
                                    name="mcpEndpoint"
                                    label="连接端点（可选）"
                                    tooltip="写入 definition.endpoint，便于团队记录 MCP 服务地址。"
                                >
                                    <Input placeholder="例如 https://mcp.example.com 或 stdio 命令说明"/>
                                </Form.Item>
                            </>
                        )}

                        <Form.Item
                            name="toolDescription"
                            label="工具说明（可选）"
                            tooltip="写入 definition.description，供列表与智能体侧展示/理解。"
                        >
                            <Input.TextArea rows={3} placeholder="一句话说明这个工具做什么、何时调用"/>
                        </Form.Item>

                        <Collapse
                            bordered={false}
                            style={{background: "transparent"}}
                            items={[
                                {
                                    key: "advanced",
                                    label: "高级：合并到 definition 的 JSON（可选）",
                                    children: (
                                        <Form.Item
                                            name="definitionExtraJson"
                                            label="额外字段"
                                            tooltip="会与上面的说明、端点合并；同名字段以这里为准。"
                                            style={{marginBottom: 0}}
                                        >
                                            <JsonTextArea
                                                rows={10}
                                                placeholder='例如 {"inputSchema":{"type":"object","properties":{}},"transport":"stdio"}'
                                                sample={{
                                                    inputSchema: {type: "object", properties: {}},
                                                    transport: "stdio",
                                                }}
                                            />
                                        </Form.Item>
                                    ),
                                },
                            ]}
                        />
                    </Form>
                </Drawer>
            </Space>
        </AppLayout>
    );
}
