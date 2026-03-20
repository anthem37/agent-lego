"use client";

import {PlusOutlined, ReloadOutlined} from "@ant-design/icons";
import {Button, Empty, Input, message, Select, Space, Table, Tag, Tooltip, Typography} from "antd";
import type {ColumnsType} from "antd/es/table";
import Link from "next/link";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {DeleteToolLink} from "@/components/tools/DeleteToolPopconfirm";
import {McpBatchImportModal} from "@/components/tools/McpBatchImportModal";
import {ToolFormDrawer} from "@/components/tools/ToolFormDrawer";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {SectionCard} from "@/components/SectionCard";
import {deleteTool, fetchLocalBuiltinToolsMeta, fetchToolTypeMeta, listToolsPage} from "@/lib/tools/api";
import {tablePaginationFriendly} from "@/lib/table-pagination";
import {toolTypeDisplayName} from "@/lib/tool-labels";
import {summarizeToolDefinition} from "@/lib/tools/summary";
import {toolTypeTagColor} from "@/lib/tools/ui";
import type {LocalBuiltinToolMetaDto, ToolDto, ToolTypeMetaDto} from "@/lib/tools/types";

export default function ToolsPage() {
    const [tools, setTools] = React.useState<ToolDto[]>([]);
    const [total, setTotal] = React.useState(0);
    const [page, setPage] = React.useState(1);
    const [pageSize, setPageSize] = React.useState(20);
    const [meta, setMeta] = React.useState<ToolTypeMetaDto[]>([]);
    const [localBuiltins, setLocalBuiltins] = React.useState<LocalBuiltinToolMetaDto[]>([]);
    const [loading, setLoading] = React.useState(false);
    const [error, setError] = React.useState<unknown>(null);
    const [searchInput, setSearchInput] = React.useState("");
    const [debouncedQ, setDebouncedQ] = React.useState("");
    /** 服务端按类型精确筛选；undefined 表示全部 */
    const [filterToolType, setFilterToolType] = React.useState<string | undefined>(undefined);

    const [drawerOpen, setDrawerOpen] = React.useState(false);
    const [drawerMode, setDrawerMode] = React.useState<"create" | "edit">("create");
    const [editing, setEditing] = React.useState<ToolDto | null>(null);
    const [deletingId, setDeletingId] = React.useState<string | null>(null);
    const [mcpBulkOpen, setMcpBulkOpen] = React.useState(false);

    React.useEffect(() => {
        const t = window.setTimeout(() => {
            const q = searchInput.trim();
            setDebouncedQ(q);
            setPage(1);
        }, 350);
        return () => window.clearTimeout(t);
    }, [searchInput]);

    /** 类型元数据、内置清单仅在首屏拉取；刷新按钮与导入成功后再拉 */
    React.useEffect(() => {
        let cancelled = false;
        void (async () => {
            try {
                const [m, builtins] = await Promise.all([
                    fetchToolTypeMeta(),
                    fetchLocalBuiltinToolsMeta(),
                ]);
                if (!cancelled) {
                    setMeta(m);
                    setLocalBuiltins(builtins);
                }
            } catch (e) {
                if (!cancelled) {
                    console.error(e);
                    message.warning("工具类型元数据加载失败，新建/编辑抽屉可能缺少类型说明");
                }
            }
        })();
        return () => {
            cancelled = true;
        };
    }, []);

    React.useEffect(() => {
        let cancelled = false;
        setLoading(true);
        void (async () => {
            try {
                const pageData = await listToolsPage({
                    page,
                    pageSize,
                    q: debouncedQ || undefined,
                    toolType: filterToolType,
                });
                if (!cancelled) {
                    setTools(pageData.items);
                    setTotal(pageData.total);
                    setError(null);
                }
            } catch (e) {
                if (!cancelled) {
                    setError(e);
                }
            } finally {
                if (!cancelled) {
                    setLoading(false);
                }
            }
        })();
        return () => {
            cancelled = true;
        };
    }, [page, pageSize, debouncedQ, filterToolType]);

    async function reload() {
        setError(null);
        setLoading(true);
        try {
            const [pageData, m, builtins] = await Promise.all([
                listToolsPage({
                    page,
                    pageSize,
                    q: debouncedQ || undefined,
                    toolType: filterToolType,
                }),
                fetchToolTypeMeta(),
                fetchLocalBuiltinToolsMeta(),
            ]);
            setTools(pageData.items);
            setTotal(pageData.total);
            setMeta(m);
            setLocalBuiltins(builtins);
        } catch (e) {
            setError(e);
        } finally {
            setLoading(false);
        }
    }

    const typeFilterOptions = React.useMemo(
        () =>
            [...meta]
                .sort((a, b) => a.code.localeCompare(b.code))
                .map((m) => ({
                    value: m.code,
                    label: `${m.label}（${m.code}）`,
                })),
        [meta],
    );

    const hasActiveListFilter = Boolean(debouncedQ || filterToolType);

    function clearListFilters() {
        setSearchInput("");
        setDebouncedQ("");
        setFilterToolType(undefined);
        setPage(1);
    }

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
                    subtitle={
                        <Tooltip
                            title="支持列表检索与类型筛选、新建/编辑/删除、详情页联调与 test-call；类型说明来自 /tools/meta/tool-types，便于扩展新类型。"
                        >
                            <span>
                                检索与类型筛选、CRUD、详情联调；MCP 支持批量导入。鼠标悬停查看说明。
                            </span>
                        </Tooltip>
                    }
                    extra={
                        <Space wrap>
                            <Button icon={<ReloadOutlined/>} onClick={() => void reload()} loading={loading}>
                                刷新
                            </Button>
                            <Button type="primary" icon={<PlusOutlined/>} onClick={openCreate}>
                                新建工具
                            </Button>
                            <Button onClick={() => setMcpBulkOpen(true)}>MCP 批量导入</Button>
                        </Space>
                    }
                />

                <ErrorAlert error={error}/>

                <SectionCard title="工具列表">
                    <Space orientation="vertical" size={12} style={{width: "100%"}}>
                        <Space wrap style={{width: "100%", justifyContent: "space-between", alignItems: "center"}}>
                            <Space wrap size={12}>
                                <Input.Search
                                    allowClear
                                    placeholder="名称 / ID / 类型 / definition 关键词…"
                                    style={{width: 320, minWidth: 200}}
                                    value={searchInput}
                                    onSearch={(v) => {
                                        setSearchInput(v);
                                        setDebouncedQ(v.trim());
                                        setPage(1);
                                    }}
                                    onChange={(e) => setSearchInput(e.target.value)}
                                />
                                <Select
                                    allowClear
                                    placeholder="工具类型"
                                    style={{minWidth: 160}}
                                    value={filterToolType}
                                    options={typeFilterOptions}
                                    onChange={(v) => {
                                        setFilterToolType(v ?? undefined);
                                        setPage(1);
                                    }}
                                />
                                {hasActiveListFilter ? (
                                    <Button type="link" onClick={clearListFilters} style={{padding: 0}}>
                                        清空条件
                                    </Button>
                                ) : null}
                            </Space>
                            <Typography.Text type="secondary">共 {total} 条</Typography.Text>
                        </Space>

                        <Table<ToolDto>
                            rowKey="id"
                            loading={loading}
                            dataSource={tools}
                            columns={columns}
                            scroll={{x: 1180}}
                            pagination={tablePaginationFriendly({
                                current: page,
                                pageSize,
                                total,
                                showSizeChanger: true,
                                pageSizeOptions: [10, 20, 50, 100],
                                onChange: (p, ps) => {
                                    setPage(p);
                                    setPageSize(ps);
                                },
                            })}
                            locale={{
                                emptyText: hasActiveListFilter ? (
                                    <Empty
                                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                                        description="没有符合条件的工具"
                                    >
                                        <Space>
                                            <Button onClick={clearListFilters}>清空筛选</Button>
                                            <Button type="primary" onClick={openCreate}>
                                                新建工具
                                            </Button>
                                        </Space>
                                    </Empty>
                                ) : (
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

                <McpBatchImportModal
                    open={mcpBulkOpen}
                    onClose={() => setMcpBulkOpen(false)}
                    onSuccess={reload}
                />
            </Space>
        </AppLayout>
    );
}
