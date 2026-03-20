"use client";

import {PlusOutlined} from "@ant-design/icons";
import {Button, Empty, Input, message, Space, Table, Tag, Tooltip, Typography} from "antd";
import type {ColumnsType} from "antd/es/table";
import Link from "next/link";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {DeleteToolLink} from "@/components/tools/DeleteToolPopconfirm";
import {ToolFormDrawer} from "@/components/tools/ToolFormDrawer";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {SectionCard} from "@/components/SectionCard";
import {deleteTool, fetchLocalBuiltinToolsMeta, fetchToolTypeMeta, listTools} from "@/lib/tools/api";
import {stringifyPretty} from "@/lib/json";
import {tablePaginationFriendly} from "@/lib/table-pagination";
import {toolTypeDisplayName} from "@/lib/tool-labels";
import {summarizeToolDefinition} from "@/lib/tools/summary";
import {toolTypeTagColor} from "@/lib/tools/ui";
import type {LocalBuiltinToolMetaDto, ToolDto, ToolTypeMetaDto} from "@/lib/tools/types";

export default function ToolsPage() {
    const [tools, setTools] = React.useState<ToolDto[]>([]);
    const [meta, setMeta] = React.useState<ToolTypeMetaDto[]>([]);
    const [localBuiltins, setLocalBuiltins] = React.useState<LocalBuiltinToolMetaDto[]>([]);
    const [loading, setLoading] = React.useState(false);
    const [error, setError] = React.useState<unknown>(null);
    const [keyword, setKeyword] = React.useState("");

    const [drawerOpen, setDrawerOpen] = React.useState(false);
    const [drawerMode, setDrawerMode] = React.useState<"create" | "edit">("create");
    const [editing, setEditing] = React.useState<ToolDto | null>(null);
    const [deletingId, setDeletingId] = React.useState<string | null>(null);

    async function reload() {
        setError(null);
        setLoading(true);
        try {
            const [list, m, builtins] = await Promise.all([
                listTools(),
                fetchToolTypeMeta(),
                fetchLocalBuiltinToolsMeta(),
            ]);
            setTools(list);
            setMeta(m);
            setLocalBuiltins(builtins);
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
        setDrawerMode("create");
        setEditing(null);
        setDrawerOpen(true);
    }

    function openEdit(record: ToolDto) {
        setDrawerMode("edit");
        setEditing(record);
        setDrawerOpen(true);
    }

    async function onDelete(id: string) {
        setError(null);
        setDeletingId(id);
        try {
            await deleteTool(id);
            message.success("已删除");
            await reload();
        } catch (e) {
            setError(e);
        } finally {
            setDeletingId(null);
        }
    }

    const filtered = React.useMemo(() => {
        const k = keyword.trim().toLowerCase();
        if (!k) {
            return tools;
        }
        return tools.filter((r) => {
            const defStr = r.definition ? stringifyPretty(r.definition).toLowerCase() : "";
            const summary = summarizeToolDefinition(r).toLowerCase();
            return (
                r.id.toLowerCase().includes(k) ||
                r.name.toLowerCase().includes(k) ||
                r.toolType.toLowerCase().includes(k) ||
                toolTypeDisplayName(r.toolType).toLowerCase().includes(k) ||
                summary.includes(k) ||
                defStr.includes(k)
            );
        });
    }, [tools, keyword]);

    const columns: ColumnsType<ToolDto> = [
        {
            title: "名称",
            dataIndex: "name",
            width: 160,
            ellipsis: true,
            render: (v: string, record) => (
                <Tooltip title={v}>
                    <Link href={`/tools/${record.id}`}>
                        <Typography.Text strong ellipsis style={{maxWidth: 140}}>
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
            render: (v: string) => <Tag color={toolTypeTagColor(v)}>{toolTypeDisplayName(v)}</Tag>,
        },
        {
            title: "配置摘要",
            key: "summary",
            width: 280,
            ellipsis: true,
            render: (_: unknown, record: ToolDto) => {
                const s = summarizeToolDefinition(record);
                return (
                    <Tooltip title={s}>
                        <Typography.Text type="secondary" ellipsis style={{maxWidth: 260}}>
                            {s}
                        </Typography.Text>
                    </Tooltip>
                );
            },
        },
        {
            title: "工具 ID",
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
            width: 180,
            render: (v: string | undefined) => v ?? "—",
        },
        {
            title: "操作",
            key: "actions",
            width: 220,
            fixed: "right",
            render: (_, record) => (
                <Space size={8} wrap>
                    <Link href={`/tools/${record.id}`}>
                        <Button type="link" size="small" style={{padding: 0}}>
                            详情
                        </Button>
                    </Link>
                    <Button type="link" size="small" style={{padding: 0}} onClick={() => openEdit(record)}>
                        编辑
                    </Button>
                    <DeleteToolLink
                        toolId={record.id}
                        deleting={deletingId === record.id}
                        onConfirm={() => void onDelete(record.id)}
                    />
                </Space>
            ),
        },
    ];

    return (
        <AppLayout>
            <Space orientation="vertical" size={16} style={{width: "100%"}}>
                <PageHeaderBlock
                    title="工具管理"
                    subtitle="完整 CRUD：列表检索、新建、编辑、删除；详情页联调。类型能力说明由后端 /tools/meta/tool-types 提供，扩展新类型时前后端可同步演进。"
                    extra={
                        <Space wrap>
                            <Button onClick={() => void reload()} loading={loading}>
                                刷新
                            </Button>
                            <Button type="primary" icon={<PlusOutlined/>} onClick={openCreate}>
                                新建工具
                            </Button>
                        </Space>
                    }
                />

                <ErrorAlert error={error}/>

                <SectionCard title="工具列表">
                    <Space orientation="vertical" size={12} style={{width: "100%"}}>
                        <Space wrap style={{width: "100%", justifyContent: "space-between", alignItems: "center"}}>
                            <Input.Search
                                allowClear
                                placeholder="按名称、类型、ID、配置摘要或 definition 片段过滤…"
                                style={{maxWidth: 400, minWidth: 240}}
                                onSearch={setKeyword}
                                onChange={(e) => setKeyword(e.target.value)}
                            />
                            <Typography.Text type="secondary">
                                共 {filtered.length} / {tools.length} 条
                            </Typography.Text>
                        </Space>

                        <Table<ToolDto>
                            rowKey="id"
                            loading={loading}
                            dataSource={filtered}
                            columns={columns}
                            scroll={{x: 1180}}
                            pagination={tablePaginationFriendly()}
                            locale={{
                                emptyText: (
                                    <Empty
                                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                                        description="暂无工具，点击「新建工具」开始注册"
                                    >
                                        <Button type="primary" onClick={openCreate}>
                                            新建工具
                                        </Button>
                                    </Empty>
                                ),
                            }}
                        />
                    </Space>
                </SectionCard>

                <ToolFormDrawer
                    open={drawerOpen}
                    mode={drawerMode}
                    editingTool={editing}
                    toolTypeMeta={meta}
                    localBuiltins={localBuiltins}
                    onClose={() => {
                        setDrawerOpen(false);
                        setEditing(null);
                    }}
                    onSaved={reload}
                />
            </Space>
        </AppLayout>
    );
}
