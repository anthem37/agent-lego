"use client";

import {
    BookOutlined,
    DatabaseOutlined,
    DeleteOutlined,
    EditOutlined,
    EyeOutlined,
    FileTextOutlined,
    MoreOutlined,
    PlusOutlined,
    ReloadOutlined,
    RightOutlined,
    SearchOutlined,
} from "@ant-design/icons";
import {
    Button,
    Card,
    Col,
    Dropdown,
    Drawer,
    Empty,
    Form,
    Input,
    InputNumber,
    Modal,
    Popconfirm,
    Row,
    Segmented,
    Select,
    Space,
    Spin,
    Table,
    Tag,
    Typography,
    message,
    theme,
} from "antd";
import type {ColumnsType} from "antd/es/table";
import type {MenuProps} from "antd/es/menu";
import React from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

import {AppLayout} from "@/components/AppLayout";
import {KbMarkdownEditor} from "@/components/kb/KbMarkdownEditor";
import {KbRichTextEditor} from "@/components/kb/KbRichTextEditor";
import {ErrorAlert} from "@/components/ErrorAlert";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {
    addKnowledge,
    createKbBase,
    deleteKbBase,
    deleteKnowledgeDocument,
    getKnowledgeDocument,
    listKbBases,
    listKnowledge,
    queryKb,
    updateKbBase,
} from "@/lib/kb/api";
import type {KbBaseDto, KbChunkStrategy, KbDocumentSummaryDto, KbKnowledgeDetailDto} from "@/lib/kb/types";
import {stringifyPretty} from "@/lib/json";
import {tablePaginationFriendly} from "@/lib/table-pagination";
import {DRAWER_WIDTH_COMPLEX} from "@/lib/ui/sizes";

const {Text, Title, Paragraph} = Typography;

type KnowledgeForm = {
    name: string;
    contentFormat: "markdown" | "html";
    content: string;
    chunkStrategy: KbChunkStrategy;
    chunkSize: number;
    overlap: number;
};

const CHUNK_STRATEGY_OPTIONS: {value: KbChunkStrategy; label: string; hint: string}[] = [
    {value: "fixed", label: "固定滑窗", hint: "按字符滑窗 + overlap，与历史默认一致"},
    {value: "paragraph", label: "按段落", hint: "空行分段，小段合并后再切"},
    {value: "hybrid", label: "混合（推荐）", hint: "段落优先，超长时在句号等处软截断"},
    {value: "markdown_sections", label: "Markdown 按标题", hint: "仅 MD：按 # 标题分节；富文本会自动回退为混合"},
];

function chunkStrategyLabel(s: string | undefined): string {
    const opt = CHUNK_STRATEGY_OPTIONS.find((o) => o.value === s);
    return opt?.label ?? (s || "固定滑窗");
}

type BaseFormCreate = {
    kbKey: string;
    name: string;
    description?: string;
};

type BaseFormEdit = {
    name: string;
    description?: string;
};

function escapeRegExp(s: string): string {
    return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function highlightSnippet(text: string, query: string): React.ReactNode {
    const q = query.trim();
    if (!q || !text) {
        return text;
    }
    try {
        const parts = text.split(new RegExp(`(${escapeRegExp(q)})`, "gi"));
        return parts.map((part, i) =>
            part.toLowerCase() === q.toLowerCase() ? (
                <mark key={i} style={{padding: "0 2px", background: "#fff566"}}>
                    {part}
                </mark>
            ) : (
                <span key={i}>{part}</span>
            ),
        );
    } catch {
        return text;
    }
}

function formatTime(iso?: string | null): string {
    if (!iso) {
        return "—";
    }
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
}

export default function KbPage() {
    const {token} = theme.useToken();

    const [bases, setBases] = React.useState<KbBaseDto[]>([]);
    const [basesLoading, setBasesLoading] = React.useState(false);
    const [selectedBaseId, setSelectedBaseId] = React.useState<string | null>(null);

    const [baseModalOpen, setBaseModalOpen] = React.useState(false);
    const [baseModalMode, setBaseModalMode] = React.useState<"create" | "edit">("create");
    const [editingBase, setEditingBase] = React.useState<KbBaseDto | null>(null);
    const [baseFormCreate] = Form.useForm<BaseFormCreate>();
    const [baseFormEdit] = Form.useForm<BaseFormEdit>();
    const [baseSubmitting, setBaseSubmitting] = React.useState(false);

    const [knowledgePage, setKnowledgePage] = React.useState(1);
    const [knowledgePageSize, setKnowledgePageSize] = React.useState(10);
    const [knowledgeTotal, setKnowledgeTotal] = React.useState(0);
    const [knowledgeRows, setKnowledgeRows] = React.useState<KbDocumentSummaryDto[]>([]);
    const [knowledgeLoading, setKnowledgeLoading] = React.useState(false);

    const [addKnowledgeOpen, setAddKnowledgeOpen] = React.useState(false);
    const [knowledgeForm] = Form.useForm<KnowledgeForm>();
    const knowledgeContentFormat = Form.useWatch("contentFormat", knowledgeForm) ?? "markdown";
    const prevKnowledgeFmtRef = React.useRef<"markdown" | "html" | null>(null);
    const [knowledgeSubmitting, setKnowledgeSubmitting] = React.useState(false);

    const [knowledgePreviewOpen, setKnowledgePreviewOpen] = React.useState(false);
    const [knowledgePreview, setKnowledgePreview] = React.useState<KbKnowledgeDetailDto | null>(null);
    const [knowledgePreviewLoading, setKnowledgePreviewLoading] = React.useState(false);

    const [queryText, setQueryText] = React.useState("");
    const [topK, setTopK] = React.useState(8);
    const [querying, setQuerying] = React.useState(false);
    const [queryChunks, setQueryChunks] = React.useState<import("@/lib/kb/types").KbChunkDto[]>([]);
    const [lastQuery, setLastQuery] = React.useState("");

    const [error, setError] = React.useState<unknown>(null);

    const selectedBase = React.useMemo(
        () => bases.find((b) => b.id === selectedBaseId) ?? null,
        [bases, selectedBaseId],
    );

    async function reloadBases() {
        setBasesLoading(true);
        setError(null);
        try {
            const list = await listKbBases();
            setBases(list);
            if (selectedBaseId && !list.some((b) => b.id === selectedBaseId)) {
                setSelectedBaseId(list[0]?.id ?? null);
            } else if (!selectedBaseId && list.length > 0) {
                setSelectedBaseId(list[0].id);
            }
        } catch (e) {
            setError(e);
        } finally {
            setBasesLoading(false);
        }
    }

    async function reloadKnowledge() {
        if (!selectedBaseId) {
            setKnowledgeRows([]);
            setKnowledgeTotal(0);
            return;
        }
        setKnowledgeLoading(true);
        setError(null);
        try {
            const page = await listKnowledge({
                baseId: selectedBaseId,
                page: knowledgePage,
                pageSize: knowledgePageSize,
            });
            setKnowledgeRows(page.items);
            setKnowledgeTotal(page.total);
        } catch (e) {
            setError(e);
        } finally {
            setKnowledgeLoading(false);
        }
    }

    React.useEffect(() => {
        void reloadBases();
    }, []);

    React.useEffect(() => {
        void reloadKnowledge();
    }, [selectedBaseId, knowledgePage, knowledgePageSize]);

    React.useEffect(() => {
        if (!addKnowledgeOpen) {
            prevKnowledgeFmtRef.current = null;
            return;
        }
        const cur = knowledgeContentFormat;
        if (prevKnowledgeFmtRef.current === null) {
            prevKnowledgeFmtRef.current = cur;
            return;
        }
        if (prevKnowledgeFmtRef.current !== cur) {
            knowledgeForm.setFieldValue("content", "");
            void message.info("已切换格式，正文已清空");
            prevKnowledgeFmtRef.current = cur;
        }
    }, [addKnowledgeOpen, knowledgeContentFormat, knowledgeForm]);

    function openCreateBase() {
        setBaseModalMode("create");
        setEditingBase(null);
        baseFormCreate.resetFields();
        setBaseModalOpen(true);
    }

    function openEditBase(row: KbBaseDto) {
        setBaseModalMode("edit");
        setEditingBase(row);
        baseFormEdit.setFieldsValue({
            name: row.name,
            description: row.description ?? "",
        });
        setBaseModalOpen(true);
    }

    async function submitBaseModal() {
        setBaseSubmitting(true);
        setError(null);
        try {
            if (baseModalMode === "create") {
                const v = await baseFormCreate.validateFields();
                const created = await createKbBase({
                    kbKey: v.kbKey.trim(),
                    name: v.name.trim(),
                    description: v.description?.trim() || undefined,
                });
                message.success("知识库已创建");
                setBaseModalOpen(false);
                await reloadBases();
                setSelectedBaseId(created.id);
            } else if (editingBase) {
                const v = await baseFormEdit.validateFields();
                await updateKbBase(editingBase.id, {
                    name: v.name.trim(),
                    description: v.description?.trim() ?? "",
                });
                message.success("知识库已更新");
                setBaseModalOpen(false);
                await reloadBases();
            }
        } catch (e) {
            if (e && typeof e === "object" && "errorFields" in e) {
                return;
            }
            setError(e);
        } finally {
            setBaseSubmitting(false);
        }
    }

    async function onDeleteBase(row: KbBaseDto) {
        setError(null);
        try {
            await deleteKbBase(row.id);
            message.success("已删除知识库及其全部知识");
            if (selectedBaseId === row.id) {
                setSelectedBaseId(null);
            }
            await reloadBases();
        } catch (e) {
            setError(e);
        }
    }

    function baseMoreMenu(b: KbBaseDto): MenuProps {
        return {
            items: [
                {
                    key: "edit",
                    icon: <EditOutlined/>,
                    label: "编辑知识库",
                    onClick: () => openEditBase(b),
                },
                {
                    key: "del",
                    icon: <DeleteOutlined/>,
                    label: "删除知识库",
                    danger: true,
                    onClick: () => {
                        Modal.confirm({
                            title: `删除知识库「${b.name}」？`,
                            content: "将同时删除其下全部知识与片段，不可恢复。",
                            okText: "删除",
                            okType: "danger",
                            cancelText: "取消",
                            onOk: () => void onDeleteBase(b),
                        });
                    },
                },
            ],
        };
    }

    function stripTagsForLen(html: string): string {
        return html.replace(/<[^>]*>/g, "").replace(/\s/g, "");
    }

    async function openKnowledgePreview(row: KbDocumentSummaryDto) {
        setKnowledgePreviewOpen(true);
        setKnowledgePreview(null);
        setKnowledgePreviewLoading(true);
        setError(null);
        try {
            const d = await getKnowledgeDocument(row.id);
            setKnowledgePreview(d);
        } catch (e) {
            setError(e);
            setKnowledgePreviewOpen(false);
        } finally {
            setKnowledgePreviewLoading(false);
        }
    }

    async function submitKnowledge() {
        if (!selectedBaseId) {
            return;
        }
        setKnowledgeSubmitting(true);
        setError(null);
        try {
            const v = await knowledgeForm.validateFields();
            const resp = await addKnowledge(selectedBaseId, {
                name: v.name.trim(),
                content: v.content,
                contentFormat: v.contentFormat,
                chunkStrategy: v.chunkStrategy,
                chunkSize: v.chunkSize,
                overlap: v.overlap,
            });
            message.success(`已添加知识，生成 ${resp.chunkCount} 个片段`);
            setAddKnowledgeOpen(false);
            knowledgeForm.resetFields();
            await reloadBases();
            await reloadKnowledge();
        } catch (e) {
            if (e && typeof e === "object" && "errorFields" in e) {
                return;
            }
            setError(e);
        } finally {
            setKnowledgeSubmitting(false);
        }
    }

    async function onDeleteKnowledge(row: KbDocumentSummaryDto) {
        setError(null);
        try {
            await deleteKnowledgeDocument(row.id);
            message.success("已删除该条知识");
            await reloadBases();
            await reloadKnowledge();
        } catch (e) {
            setError(e);
        }
    }

    async function runQuery() {
        if (!selectedBaseId) {
            message.warning("请先在左侧选中一个知识库");
            return;
        }
        const q = queryText.trim();
        if (!q) {
            message.warning("请输入检索内容");
            return;
        }
        setError(null);
        setQuerying(true);
        try {
            const resp = await queryKb({baseId: selectedBaseId, queryText: q, topK});
            setQueryChunks(resp.chunks);
            setLastQuery(q);
            message.info(`命中 ${resp.chunks.length} 条片段`);
        } catch (e) {
            setError(e);
        } finally {
            setQuerying(false);
        }
    }

    const knowledgeColumns: ColumnsType<KbDocumentSummaryDto> = [
        {
            title: "知识标题",
            dataIndex: "name",
            ellipsis: true,
            render: (v: string) => (
                <Space>
                    <FileTextOutlined style={{color: token.colorTextSecondary}}/>
                    <Text strong>{v}</Text>
                </Space>
            ),
        },
        {
            title: "格式",
            dataIndex: "contentFormat",
            width: 96,
            render: (f: string | undefined) =>
                f === "html" ? (
                    <Tag color="purple">富文本</Tag>
                ) : (
                    <Tag color="cyan">Markdown</Tag>
                ),
        },
        {
            title: "分片",
            dataIndex: "chunkStrategy",
            width: 112,
            ellipsis: true,
            render: (s: string | undefined) => (
                <Tag color="geekblue" style={{margin: 0}}>
                    {chunkStrategyLabel(s)}
                </Tag>
            ),
        },
        {
            title: "片段数",
            dataIndex: "chunkCount",
            width: 90,
            render: (n: number) => <Tag>{n}</Tag>,
        },
        {
            title: "创建时间",
            dataIndex: "createdAt",
            width: 170,
            render: (t: string | undefined) => <Text type="secondary">{formatTime(t)}</Text>,
        },
        {
            title: "操作",
            key: "actions",
            width: 148,
            align: "right",
            render: (_, row) => (
                <Space size={0} wrap={false}>
                    <Button type="link" size="small" icon={<EyeOutlined/>} onClick={() => void openKnowledgePreview(row)}>
                        查看
                    </Button>
                    <Popconfirm title="删除该条知识及片段？" onConfirm={() => void onDeleteKnowledge(row)}>
                        <Button type="link" size="small" danger icon={<DeleteOutlined/>}/>
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    const siderBg = token.colorFillAlter;
    const rightBg = token.colorBgContainer;
    const linkBarBg = token.colorPrimaryBg;
    const nestAccent = token.colorPrimary;

    return (
        <AppLayout>
            <Space orientation="vertical" size={16} style={{width: "100%"}}>
                <PageHeaderBlock
                    title="知识库管理"
                    subtitle="左侧选择「知识库」；右侧固定展示该库下的「知识」——与资源管理器、邮箱「文件夹→邮件」的习惯一致，关联一目了然。"
                    extra={
                        <Button icon={<ReloadOutlined/>} onClick={() => void reloadBases()}>
                            全部刷新
                        </Button>
                    }
                />

                <ErrorAlert error={error}/>

                {/* 主从一体：左库右文 */}
                <div
                    style={{
                        border: `1px solid ${token.colorBorderSecondary}`,
                        borderRadius: token.borderRadiusLG,
                        overflow: "hidden",
                        minHeight: 520,
                        background: rightBg,
                    }}
                >
                    <Row style={{minHeight: 520, alignItems: "stretch"}}>
                        {/* 左：知识库列表（主） */}
                        <Col
                            xs={24}
                            lg={7}
                            style={{
                                background: siderBg,
                                borderRight: `1px solid ${token.colorBorderSecondary}`,
                                display: "flex",
                                flexDirection: "column",
                            }}
                        >
                            <div
                                style={{
                                    padding: "14px 16px",
                                    borderBottom: `1px solid ${token.colorBorderSecondary}`,
                                    background: token.colorFillQuaternary,
                                }}
                            >
                                <Space style={{width: "100%", justifyContent: "space-between"}} align="center">
                                    <Space size={6}>
                                        <DatabaseOutlined style={{color: token.colorPrimary, fontSize: 18}}/>
                                        <Text strong style={{fontSize: 15}}>
                                            知识库
                                        </Text>
                                    </Space>
                                    <Button type="primary" size="small" icon={<PlusOutlined/>} onClick={openCreateBase}>
                                        新建
                                    </Button>
                                </Space>
                                <Paragraph type="secondary" style={{margin: "8px 0 0", fontSize: 12, marginBottom: 0}}>
                                    点选其一 → 右侧即显示<strong>该库内</strong>的知识条目（不再用下拉框切换，避免「库」和「文」脱节）。
                                </Paragraph>
                            </div>
                            <div style={{flex: 1, overflow: "auto", padding: "8px 0"}}>
                                <Spin spinning={basesLoading}>
                                    {bases.length === 0 && !basesLoading ? (
                                        <Empty
                                            style={{margin: "32px 16px"}}
                                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                                            description="还没有知识库"
                                        >
                                            <Button type="primary" size="small" onClick={openCreateBase}>
                                                先创建一个
                                            </Button>
                                        </Empty>
                                    ) : (
                                        <Space orientation="vertical" size={4} style={{width: "100%", padding: "0 10px 12px"}}>
                                            {bases.map((b) => {
                                                const active = b.id === selectedBaseId;
                                                return (
                                                    <div
                                                        key={b.id}
                                                        role="button"
                                                        tabIndex={0}
                                                        onClick={() => {
                                                            setSelectedBaseId(b.id);
                                                            setKnowledgePage(1);
                                                        }}
                                                        onKeyDown={(e) => {
                                                            if (e.key === "Enter" || e.key === " ") {
                                                                e.preventDefault();
                                                                setSelectedBaseId(b.id);
                                                                setKnowledgePage(1);
                                                            }
                                                        }}
                                                        style={{
                                                            position: "relative",
                                                            padding: "12px 40px 12px 14px",
                                                            borderRadius: token.borderRadius,
                                                            cursor: "pointer",
                                                            border: `1px solid ${active ? token.colorPrimaryBorder : "transparent"}`,
                                                            background: active ? token.colorPrimaryBg : "transparent",
                                                            boxShadow: active ? `inset 3px 0 0 ${nestAccent}` : undefined,
                                                            transition: "background 0.15s, box-shadow 0.15s",
                                                        }}
                                                    >
                                                        <Space style={{width: "100%", justifyContent: "space-between"}} align="start">
                                                            <Space orientation="vertical" size={2} style={{minWidth: 0}}>
                                                                <Text strong ellipsis style={{maxWidth: "100%"}}>
                                                                    {b.name}
                                                                </Text>
                                                                <Text type="secondary" style={{fontSize: 12}} ellipsis>
                                                                    <Text code style={{fontSize: 11}}>
                                                                        {b.kbKey}
                                                                    </Text>
                                                                </Text>
                                                                <Space size={6} wrap style={{marginTop: 4}}>
                                                                    <Tag color={active ? "processing" : "default"} style={{margin: 0}}>
                                                                        {b.documentCount} 条知识
                                                                    </Tag>
                                                                    {b.lastIngestAt ? (
                                                                        <Text type="secondary" style={{fontSize: 11}}>
                                                                            更新 {formatTime(b.lastIngestAt)}
                                                                        </Text>
                                                                    ) : null}
                                                                </Space>
                                                            </Space>
                                                        </Space>
                                                        <div
                                                            style={{position: "absolute", right: 6, top: 10}}
                                                            onClick={(e) => e.stopPropagation()}
                                                        >
                                                            <Dropdown menu={baseMoreMenu(b)} trigger={["click"]}>
                                                                <Button type="text" size="small" icon={<MoreOutlined/>} aria-label="更多"/>
                                                            </Dropdown>
                                                        </div>
                                                    </div>
                                                );
                                            })}
                                        </Space>
                                    )}
                                </Spin>
                            </div>
                        </Col>

                        {/* 右：当前库下的知识（从） */}
                        <Col xs={24} lg={17} style={{display: "flex", flexDirection: "column", minWidth: 0}}>
                            {!selectedBaseId ? (
                                <div style={{flex: 1, display: "flex", alignItems: "center", justifyContent: "center", padding: 32}}>
                                    <Empty
                                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                                        description={
                                            <span>
                                                请先在<strong>左侧</strong>点击选择一个知识库
                                                <br/>
                                                <Text type="secondary">右侧区域始终表示「当前选中库」里的内容</Text>
                                            </span>
                                        }
                                    />
                                </div>
                            ) : (
                                <>
                                    {/* 显式关联条：库 → 包含 → 知识 */}
                                    <div
                                        style={{
                                            padding: "14px 20px",
                                            background: linkBarBg,
                                            borderBottom: `1px solid ${token.colorBorderSecondary}`,
                                        }}
                                    >
                                        <Space wrap size={[8, 8]} align="center">
                                            <Tag icon={<DatabaseOutlined/>} color="blue">
                                                知识库
                                            </Tag>
                                            <Text strong style={{fontSize: 15}}>
                                                {selectedBase?.name}
                                            </Text>
                                            <RightOutlined style={{color: token.colorTextQuaternary}}/>
                                            <Tag icon={<BookOutlined/>} color="cyan">
                                                包含
                                            </Tag>
                                            <Text>
                                                <Text strong>{knowledgeTotal}</Text>
                                                <Text type="secondary"> 条知识文档</Text>
                                            </Text>
                                            <Text type="secondary">·</Text>
                                            <Text type="secondary">
                                                智能体绑定键 <Text code>{selectedBase?.kbKey}</Text>
                                            </Text>
                                        </Space>
                                        <Paragraph type="secondary" style={{margin: "10px 0 0", fontSize: 12, marginBottom: 0}}>
                                            下方表格、添加知识、召回试用均只作用于「<Text strong>{selectedBase?.name}</Text>」这一知识库，不会串库。
                                        </Paragraph>
                                    </div>

                                    <div style={{flex: 1, padding: "16px 20px 24px", overflow: "auto"}}>
                                        <div
                                            style={{
                                                borderLeft: `4px solid ${nestAccent}`,
                                                paddingLeft: 16,
                                                background: token.colorFillQuaternary,
                                                borderRadius: token.borderRadius,
                                                paddingTop: 16,
                                                paddingRight: 16,
                                                paddingBottom: 8,
                                                marginBottom: 16,
                                            }}
                                        >
                                            <Space wrap style={{marginBottom: 12, width: "100%", justifyContent: "space-between"}}>
                                                <Space>
                                                    <FileTextOutlined style={{fontSize: 18, color: nestAccent}}/>
                                                    <Title level={5} style={{margin: 0}}>
                                                        该库下的知识
                                                    </Title>
                                                    <Tag>从属于左侧所选</Tag>
                                                </Space>
                                                <Space wrap>
                                                    <Button icon={<ReloadOutlined/>} onClick={() => void reloadKnowledge()}>
                                                        刷新列表
                                                    </Button>
                                                    <Button
                                                        type="primary"
                                                        icon={<PlusOutlined/>}
                                                        onClick={() => {
                                                            knowledgeForm.resetFields();
                                                            knowledgeForm.setFieldsValue({
                                                                chunkSize: 800,
                                                                overlap: 100,
                                                                contentFormat: "markdown",
                                                                chunkStrategy: "fixed",
                                                            });
                                                            setAddKnowledgeOpen(true);
                                                        }}
                                                    >
                                                        添加知识
                                                    </Button>
                                                </Space>
                                            </Space>

                                            {knowledgeTotal === 0 && !knowledgeLoading ? (
                                                <Empty description="该库下还没有知识">
                                                    <Button
                                                        type="primary"
                                                        icon={<PlusOutlined/>}
                                                        onClick={() => {
                                                            knowledgeForm.resetFields();
                                                            knowledgeForm.setFieldsValue({
                                                                chunkSize: 800,
                                                                overlap: 100,
                                                                contentFormat: "markdown",
                                                                chunkStrategy: "fixed",
                                                            });
                                                            setAddKnowledgeOpen(true);
                                                        }}
                                                    >
                                                        添加第一条
                                                    </Button>
                                                </Empty>
                                            ) : (
                                                <Table<KbDocumentSummaryDto>
                                                    rowKey="id"
                                                    size="small"
                                                    loading={knowledgeLoading}
                                                    dataSource={knowledgeRows}
                                                    columns={knowledgeColumns}
                                                    pagination={tablePaginationFriendly({
                                                        current: knowledgePage,
                                                        pageSize: knowledgePageSize,
                                                        total: knowledgeTotal,
                                                        showSizeChanger: true,
                                                        pageSizeOptions: [10, 20, 50],
                                                        onChange: (p, ps) => {
                                                            setKnowledgePage(p);
                                                            setKnowledgePageSize(ps);
                                                        },
                                                    })}
                                                />
                                            )}
                                        </div>

                                        <Title level={5} style={{marginBottom: 12}}>
                                            <SearchOutlined/> 召回试用（仅当前库）
                                        </Title>
                                        <Input.Search
                                            size="large"
                                            placeholder={`在「${selectedBase?.name ?? ""}」内检索片段…`}
                                            value={queryText}
                                            onChange={(e) => setQueryText(e.target.value)}
                                            onSearch={() => void runQuery()}
                                            enterButton={
                                                <Button type="primary" loading={querying}>
                                                    检索
                                                </Button>
                                            }
                                        />
                                        <Space style={{marginTop: 12}} align="center">
                                            <Text type="secondary">topK</Text>
                                            <InputNumber min={1} max={100} value={topK} onChange={(v) => setTopK(typeof v === "number" ? v : 8)}/>
                                        </Space>

                                        <Spin spinning={querying} style={{marginTop: 16, display: "block"}}>
                                            {queryChunks.length === 0 && !querying ? (
                                                <Empty description="尚无结果" image={Empty.PRESENTED_IMAGE_SIMPLE}/>
                                            ) : (
                                                <Space orientation="vertical" size={16} style={{width: "100%"}}>
                                                    {queryChunks.map((item) => (
                                                        <Card key={item.id} size="small" type="inner" style={{width: "100%"}}>
                                                            <Space orientation="vertical" size={8} style={{width: "100%"}}>
                                                                <Space wrap>
                                                                    <Text type="secondary">来源知识</Text>
                                                                    <Text strong>{item.documentName ?? "—"}</Text>
                                                                    <Tag>片段 #{item.chunkIndex}</Tag>
                                                                </Space>
                                                                <Paragraph style={{marginBottom: 0, whiteSpace: "pre-wrap"}}>
                                                                    {highlightSnippet(item.content, lastQuery)}
                                                                </Paragraph>
                                                                {item.metadata && Object.keys(item.metadata).length > 0 ? (
                                                                    <details>
                                                                        <summary style={{cursor: "pointer", fontSize: 12}}>metadata</summary>
                                                                        <pre style={{margin: "8px 0 0", fontSize: 11, whiteSpace: "pre-wrap"}}>
                                                                            {stringifyPretty(item.metadata)}
                                                                        </pre>
                                                                    </details>
                                                                ) : null}
                                                            </Space>
                                                        </Card>
                                                    ))}
                                                </Space>
                                            )}
                                        </Spin>
                                    </div>
                                </>
                            )}
                        </Col>
                    </Row>
                </div>
            </Space>

            <Modal
                title={baseModalMode === "create" ? "新建知识库" : "编辑知识库"}
                open={baseModalOpen}
                onCancel={() => setBaseModalOpen(false)}
                onOk={() => void submitBaseModal()}
                confirmLoading={baseSubmitting}
                destroyOnHidden
                width={520}
            >
                {baseModalMode === "create" ? (
                    <Form<BaseFormCreate> form={baseFormCreate} layout="vertical">
                        <Form.Item
                            name="kbKey"
                            label="绑定键 kbKey"
                            rules={[{required: true, message: "必填，创建后不可改"}]}
                            extra="与智能体 knowledgeBasePolicy 中一致，建议英文+下划线"
                        >
                            <Input placeholder="例如 product_faq"/>
                        </Form.Item>
                        <Form.Item name="name" label="显示名称" rules={[{required: true, message: "必填"}]}>
                            <Input placeholder="例如 产品帮助文档库"/>
                        </Form.Item>
                        <Form.Item name="description" label="描述（可选）">
                            <Input.TextArea rows={3} placeholder="用途说明"/>
                        </Form.Item>
                    </Form>
                ) : (
                    <Form<BaseFormEdit> form={baseFormEdit} layout="vertical">
                        <Form.Item label="绑定键">
                            <Text code>{editingBase?.kbKey}</Text>
                            <Paragraph type="secondary" style={{marginBottom: 0, fontSize: 12}}>
                                绑定键不可修改；若需更换请新建知识库并迁移知识。
                            </Paragraph>
                        </Form.Item>
                        <Form.Item name="name" label="显示名称" rules={[{required: true}]}>
                            <Input/>
                        </Form.Item>
                        <Form.Item name="description" label="描述">
                            <Input.TextArea rows={3}/>
                        </Form.Item>
                    </Form>
                )}
            </Modal>

            <Drawer
                title={
                    <Space orientation="vertical" size={0}>
                        <Title level={5} style={{margin: 0}}>
                            添加知识
                        </Title>
                        {selectedBase ? (
                            <Text type="secondary">
                                写入到知识库：<Text strong>{selectedBase.name}</Text>（<Text code>{selectedBase.kbKey}</Text>）
                            </Text>
                        ) : null}
                    </Space>
                }
                size={DRAWER_WIDTH_COMPLEX}
                open={addKnowledgeOpen}
                onClose={() => setAddKnowledgeOpen(false)}
                destroyOnHidden
                styles={{
                    body: {
                        padding: token.paddingLG,
                    },
                    footer: {
                        padding: `${token.paddingSM}px ${token.paddingLG}px`,
                        borderTop: `1px solid ${token.colorBorderSecondary}`,
                    },
                }}
                footer={
                    <Space style={{width: "100%", justifyContent: "flex-end"}}>
                        <Button onClick={() => setAddKnowledgeOpen(false)}>取消</Button>
                        <Button type="primary" loading={knowledgeSubmitting} onClick={() => void submitKnowledge()}>
                            保存并分片
                        </Button>
                    </Space>
                }
            >
                <Form<KnowledgeForm>
                    form={knowledgeForm}
                    layout="vertical"
                    style={{width: "100%"}}
                    initialValues={{
                        chunkSize: 800,
                        overlap: 100,
                        contentFormat: "markdown",
                        chunkStrategy: "fixed",
                    }}
                >
                    <Form.Item name="name" label="知识标题" rules={[{required: true}]}>
                        <Input placeholder="如：退换货政策 2025"/>
                    </Form.Item>
                    <Form.Item name="contentFormat" label="正文格式">
                        <Segmented
                            options={[
                                {label: "Markdown", value: "markdown"},
                                {label: "富文本 (HTML)", value: "html"},
                            ]}
                        />
                    </Form.Item>
                    <Paragraph type="secondary" style={{marginTop: -8, fontSize: 12}}>
                        召回与分片使用<strong>纯文本</strong>：Markdown 会先渲染再去标记；HTML 会去掉标签后再切分。正文分别按 MD / HTML 原文入库。
                    </Paragraph>
                    <Form.Item
                        key={knowledgeContentFormat}
                        name="content"
                        label="正文"
                        dependencies={["contentFormat"]}
                        rules={[
                            {
                                validator: async (_, v: string) => {
                                    const fmt = knowledgeForm.getFieldValue("contentFormat") ?? "markdown";
                                    if (fmt === "html") {
                                        if (!v || stripTagsForLen(v).length === 0) {
                                            throw new Error("富文本中需要包含可读文字");
                                        }
                                    } else if (!v || !String(v).trim()) {
                                        throw new Error("请输入 Markdown 正文");
                                    }
                                    if (v && v.length > 500_000) {
                                        throw new Error("正文超过 50 万字符");
                                    }
                                },
                            },
                        ]}
                    >
                        {knowledgeContentFormat === "html" ? (
                            <KbRichTextEditor/>
                        ) : (
                            <KbMarkdownEditor/>
                        )}
                    </Form.Item>
                    <Form.Item name="chunkStrategy" label="分片策略">
                        <Select
                            options={CHUNK_STRATEGY_OPTIONS.map((o) => ({
                                value: o.value,
                                label: `${o.label} — ${o.hint}`,
                            }))}
                        />
                    </Form.Item>
                    <Paragraph type="secondary" style={{marginTop: -12, marginBottom: 8, fontSize: 12}}>
                        每条知识可单独选择策略；「Markdown 按标题」在富文本正文下会自动按混合策略处理。
                    </Paragraph>
                    <Space size={24} wrap>
                        <Form.Item name="chunkSize" label="chunkSize">
                            <InputNumber min={100} style={{width: 140}}/>
                        </Form.Item>
                        <Form.Item name="overlap" label="overlap">
                            <InputNumber min={0} style={{width: 140}}/>
                        </Form.Item>
                    </Space>
                </Form>
            </Drawer>

            <Modal
                title={knowledgePreview?.name ? `查看：${knowledgePreview.name}` : "查看知识"}
                open={knowledgePreviewOpen}
                onCancel={() => setKnowledgePreviewOpen(false)}
                footer={null}
                width={760}
                destroyOnHidden
            >
                <Spin spinning={knowledgePreviewLoading}>
                    {knowledgePreview ? (
                        <Space orientation="vertical" size={12} style={{width: "100%"}}>
                            <Space wrap>
                                <Text type="secondary">格式</Text>
                                {knowledgePreview.contentFormat === "html" ? (
                                    <Tag color="purple">富文本</Tag>
                                ) : (
                                    <Tag color="cyan">Markdown</Tag>
                                )}
                                <Text type="secondary">分片</Text>
                                <Tag color="geekblue">{chunkStrategyLabel(knowledgePreview.chunkStrategy)}</Tag>
                                <Text type="secondary">绑定键</Text>
                                <Text code>{knowledgePreview.kbKey}</Text>
                            </Space>
                            {knowledgePreview.contentFormat === "html" ? (
                                <iframe
                                    title="知识 HTML 预览"
                                    sandbox=""
                                    srcDoc={knowledgePreview.contentRich ?? ""}
                                    style={{
                                        width: "100%",
                                        height: 440,
                                        border: `1px solid ${token.colorBorderSecondary}`,
                                        borderRadius: token.borderRadius,
                                    }}
                                />
                            ) : (
                                <div
                                    className="kb-md-preview"
                                    style={{
                                        maxHeight: 440,
                                        overflow: "auto",
                                        padding: 12,
                                        border: `1px solid ${token.colorBorderSecondary}`,
                                        borderRadius: token.borderRadius,
                                        background: token.colorBgContainer,
                                    }}
                                >
                                    <ReactMarkdown remarkPlugins={[remarkGfm]}>
                                        {knowledgePreview.contentRich ?? ""}
                                    </ReactMarkdown>
                                </div>
                            )}
                        </Space>
                    ) : null}
                </Spin>
            </Modal>
        </AppLayout>
    );
}
