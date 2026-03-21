"use client";

import {
    BookOutlined,
    DeleteOutlined,
    DownOutlined,
    EyeOutlined,
    FileAddOutlined,
    MinusCircleOutlined,
    PlusOutlined,
    ReloadOutlined,
    SearchOutlined,
} from "@ant-design/icons";
import {
    Alert,
    Button,
    Card,
    Col,
    Drawer,
    Dropdown,
    Empty,
    Form,
    Input,
    InputNumber,
    List,
    message,
    Modal,
    Popconfirm,
    Row,
    Select,
    Space,
    Spin,
    Statistic,
    Table,
    Tabs,
    Tag,
    Tooltip,
    Typography,
} from "antd";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {request} from "@/lib/api/request";
import {KbMarkdownField} from "@/components/kb/KbMarkdownField";
import {KbMarkdownPreview} from "@/components/kb/KbMarkdownPreview";
import {
    createKbCollection,
    deleteKbCollection,
    deleteKbDocument,
    getKbDocument,
    ingestKbDocument,
    listKbChunkStrategies,
    listKbCollections,
    listKbDocuments,
    renderKbDocumentBody,
} from "@/lib/kb/api";
import type {KbChunkStrategyMetaDto, KbCollectionDto, KbDocumentDto} from "@/lib/kb/types";
import {kbToolChipText, kbToolOptionDescription, kbToolPrimaryLabel, kbToolSearchBlob} from "@/lib/kb/tool-labels";
import type {ToolDto} from "@/lib/tools/types";
import {type ModelOptionRow, toModelSelectOptions} from "@/lib/model-select-options";
import {listToolsPage} from "@/lib/tools/api";
import {tablePaginationFriendly} from "@/lib/table-pagination";

type CreateCollectionForm = {
    name: string;
    description?: string;
    embeddingModelId: string;
    chunkStrategy: string;
    maxChars: number;
    overlap: number;
    headingLevel?: number;
    leadMaxChars?: number;
};

type ToolBindingRowForm = {
    placeholder?: string;
    toolId?: string;
    jsonPath?: string;
};

type IngestForm = {
    title: string;
    body: string;
    /** 相似问列表，每项一条；提交时空串会被过滤 */
    similarQueries?: string[];
    linkedToolIds?: string[];
    toolBindingRows?: ToolBindingRowForm[];
};

const MAX_SIMILAR_QUERIES = 32;
const MAX_SIMILAR_QUERY_CHARS = 512;

function formatDateTime(iso?: string): string {
    if (!iso) {
        return "—";
    }
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) {
        return iso;
    }
    return d.toLocaleString("zh-CN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
    });
}

function chunkStrategyLabel(meta: KbChunkStrategyMetaDto[], code?: string): string {
    if (!code) {
        return "—";
    }
    return meta.find((m) => m.value === code)?.label ?? code;
}

function documentStatusTag(status: string): React.ReactNode {
    const s = (status ?? "").toUpperCase();
    if (s === "READY") {
        return <Tag color="success">就绪</Tag>;
    }
    if (s === "PENDING" || s === "PROCESSING") {
        return <Tag color="processing">处理中</Tag>;
    }
    if (s === "FAILED") {
        return <Tag color="error">失败</Tag>;
    }
    return <Tag>{status || "—"}</Tag>;
}

export default function KnowledgeBasePage() {
    const [error, setError] = React.useState<unknown>(null);
    const [modelRows, setModelRows] = React.useState<ModelOptionRow[]>([]);
    const [collections, setCollections] = React.useState<KbCollectionDto[]>([]);
    const [documents, setDocuments] = React.useState<KbDocumentDto[]>([]);
    const [loadingCollections, setLoadingCollections] = React.useState(false);
    const [loadingDocs, setLoadingDocs] = React.useState(false);
    const [refreshing, setRefreshing] = React.useState(false);
    const [creating, setCreating] = React.useState(false);
    const [ingesting, setIngesting] = React.useState(false);
    const [deletingCollectionId, setDeletingCollectionId] = React.useState<string | null>(null);
    const [deletingDocumentId, setDeletingDocumentId] = React.useState<string | null>(null);
    const [selectedCollectionId, setSelectedCollectionId] = React.useState<string | undefined>();
    const [collectionQuery, setCollectionQuery] = React.useState("");
    const [createModalOpen, setCreateModalOpen] = React.useState(false);
    const [ingestDrawerOpen, setIngestDrawerOpen] = React.useState(false);
    const [viewDocOpen, setViewDocOpen] = React.useState(false);
    const [viewDocLoading, setViewDocLoading] = React.useState(false);
    const [viewDocDetail, setViewDocDetail] = React.useState<KbDocumentDto | null>(null);
    const [toolOutputsPreviewJson, setToolOutputsPreviewJson] = React.useState("{}");
    const [renderedPreviewBody, setRenderedPreviewBody] = React.useState<string | null>(null);
    const [previewLoading, setPreviewLoading] = React.useState(false);
    const [chunkMeta, setChunkMeta] = React.useState<KbChunkStrategyMetaDto[]>([]);
    const [ingestToolCatalog, setIngestToolCatalog] = React.useState<ToolDto[]>([]);
    const [loadingToolPicker, setLoadingToolPicker] = React.useState(false);
    /** 查看文档抽屉：解析 linkedToolIds 展示名称 */
    const [viewToolById, setViewToolById] = React.useState<Record<string, ToolDto>>({});
    const [loadingViewTools, setLoadingViewTools] = React.useState(false);
    const [collectionForm] = Form.useForm<CreateCollectionForm>();
    const [ingestForm] = Form.useForm<IngestForm>();
    const watchedLinkedToolIds = Form.useWatch("linkedToolIds", ingestForm);

    const embeddingModelRows = React.useMemo(
        () =>
            modelRows.filter(
                (m) =>
                    m.chatProvider === false ||
                    (m.chatProvider !== true && m.provider.toUpperCase().includes("EMBEDDING")),
            ),
        [modelRows],
    );

    const filteredCollections = React.useMemo(() => {
        const q = collectionQuery.trim().toLowerCase();
        if (!q) {
            return collections;
        }
        return collections.filter(
            (c) =>
                c.name.toLowerCase().includes(q) ||
                c.id.toLowerCase().includes(q) ||
                (c.embeddingModelId ?? "").toLowerCase().includes(q),
        );
    }, [collections, collectionQuery]);

    const selectedCollection = React.useMemo(
        () => collections.find((c) => c.id === selectedCollectionId),
        [collections, selectedCollectionId],
    );

    const toolById = React.useMemo(() => {
        const m = new Map<string, ToolDto>();
        for (const t of ingestToolCatalog) {
            m.set(t.id, t);
        }
        return m;
    }, [ingestToolCatalog]);

    const toolSelectOptions = React.useMemo(
        () =>
            ingestToolCatalog.map((t) => ({
                value: t.id,
                label: (
                    <div style={{lineHeight: 1.35}}>
                        <div>{kbToolPrimaryLabel(t)}</div>
                        <Typography.Text type="secondary" style={{fontSize: 11}}>
                            {kbToolOptionDescription(t)}
                        </Typography.Text>
                    </div>
                ),
            })),
        [ingestToolCatalog],
    );

    const bindingToolSelectOptions = React.useMemo(() => {
        const lk = new Set((watchedLinkedToolIds ?? []).filter(Boolean) as string[]);
        return ingestToolCatalog
            .filter((t) => lk.has(t.id))
            .map((t) => ({
                value: t.id,
                label: `${kbToolPrimaryLabel(t)} · ${kbToolOptionDescription(t)}`,
            }));
    }, [ingestToolCatalog, watchedLinkedToolIds]);

    /** 取消关联工具时，同步清空映射行里已不在列表中的 toolId，避免下拉「无选项却显示 id」 */
    React.useEffect(() => {
        if (!ingestDrawerOpen) {
            return;
        }
        const linked = new Set((watchedLinkedToolIds ?? []).filter(Boolean) as string[]);
        const rows = ingestForm.getFieldValue("toolBindingRows") as ToolBindingRowForm[] | undefined;
        if (!rows?.length) {
            return;
        }
        let changed = false;
        const next = rows.map((r) => {
            if (r.toolId && !linked.has(r.toolId)) {
                changed = true;
                return {...r, toolId: undefined};
            }
            return r;
        });
        if (changed) {
            ingestForm.setFieldsValue({toolBindingRows: next});
        }
    }, [ingestDrawerOpen, watchedLinkedToolIds, ingestForm]);

    const reloadBootstrap = React.useCallback(async (opts?: { toast?: boolean }) => {
        setRefreshing(true);
        setLoadingCollections(true);
        setError(null);
        try {
            const [models, cols, meta] = await Promise.all([
                request<ModelOptionRow[]>("/models")
                    .then((d) => (Array.isArray(d) ? d : []))
                    .catch(() => [] as ModelOptionRow[]),
                listKbCollections(),
                listKbChunkStrategies().catch(() => [] as KbChunkStrategyMetaDto[]),
            ]);
            setModelRows(models);
            setCollections(cols);
            setChunkMeta(meta);
            if (opts?.toast) {
                message.success("列表已刷新");
            }
        } catch (e) {
            setError(e);
        } finally {
            setRefreshing(false);
            setLoadingCollections(false);
        }
    }, []);

    const applyChunkStrategyDefaults = React.useCallback(
        (strategy: string) => {
            const def = chunkMeta.find((x) => x.value === strategy);
            const p = def?.defaultParams ?? {};
            collectionForm.setFieldsValue({
                maxChars: typeof p.maxChars === "number" ? p.maxChars : 900,
                overlap: typeof p.overlap === "number" ? p.overlap : 0,
                headingLevel: typeof p.headingLevel === "number" ? p.headingLevel : 2,
                leadMaxChars: typeof p.leadMaxChars === "number" ? p.leadMaxChars : 512,
            });
        },
        [chunkMeta, collectionForm],
    );

    React.useEffect(() => {
        void reloadBootstrap();
    }, [reloadBootstrap]);

    React.useEffect(() => {
        if (!selectedCollectionId) {
            setDocuments([]);
            return;
        }
        const ac = new AbortController();
        let cancelled = false;
        setLoadingDocs(true);
        setError(null);
        void listKbDocuments(selectedCollectionId, ac.signal)
            .then((d) => {
                if (!cancelled) {
                    setDocuments(d);
                }
            })
            .catch((e) => {
                if (!cancelled && !ac.signal.aborted) {
                    setError(e);
                }
            })
            .finally(() => {
                if (!cancelled) {
                    setLoadingDocs(false);
                }
            });
        return () => {
            cancelled = true;
            ac.abort();
        };
    }, [selectedCollectionId]);

    React.useEffect(() => {
        if (!ingestDrawerOpen) {
            return;
        }
        let cancelled = false;
        setLoadingToolPicker(true);
        void listToolsPage({page: 1, pageSize: 200})
            .then((p) => {
                if (cancelled) {
                    return;
                }
                setIngestToolCatalog(Array.isArray(p.items) ? p.items : []);
            })
            .catch(() => {
                if (!cancelled) {
                    setIngestToolCatalog([]);
                }
            })
            .finally(() => {
                if (!cancelled) {
                    setLoadingToolPicker(false);
                }
            });
        return () => {
            cancelled = true;
        };
    }, [ingestDrawerOpen]);

    React.useEffect(() => {
        if (!viewDocOpen) {
            setViewToolById({});
            setLoadingViewTools(false);
            return;
        }
        const ids = viewDocDetail?.linkedToolIds?.filter(Boolean) ?? [];
        if (ids.length === 0) {
            setViewToolById({});
            setLoadingViewTools(false);
            return;
        }
        let cancelled = false;
        setLoadingViewTools(true);
        void listToolsPage({page: 1, pageSize: 200})
            .then((p) => {
                if (cancelled) {
                    return;
                }
                const next: Record<string, ToolDto> = {};
                for (const t of p.items ?? []) {
                    next[t.id] = t;
                }
                setViewToolById(next);
            })
            .catch(() => {
                if (!cancelled) {
                    setViewToolById({});
                }
            })
            .finally(() => {
                if (!cancelled) {
                    setLoadingViewTools(false);
                }
            });
        return () => {
            cancelled = true;
        };
    }, [viewDocOpen, viewDocDetail?.id, (viewDocDetail?.linkedToolIds ?? []).join("|")]);

    async function onCreateCollection(values: CreateCollectionForm) {
        setCreating(true);
        setError(null);
        try {
            const chunkParams: Record<string, unknown> = {
                maxChars: values.maxChars,
                overlap: values.overlap ?? 0,
            };
            if (values.chunkStrategy === "HEADING_SECTION") {
                chunkParams.headingLevel = values.headingLevel ?? 2;
                chunkParams.leadMaxChars = values.leadMaxChars ?? 512;
                chunkParams.overlap = 0;
            }
            const created = await createKbCollection({
                name: values.name,
                description: values.description ?? "",
                embeddingModelId: values.embeddingModelId,
                chunkStrategy: values.chunkStrategy,
                chunkParams,
            });
            message.success("集合已创建");
            collectionForm.resetFields();
            setCollections((prev) => [created, ...prev.filter((c) => c.id !== created.id)]);
            setCreateModalOpen(false);
            setSelectedCollectionId(created.id);
        } catch (e) {
            setError(e);
        } finally {
            setCreating(false);
        }
    }

    async function onIngest(values: IngestForm) {
        if (!selectedCollectionId) {
            message.warning("请先选择集合");
            return;
        }
        setIngesting(true);
        setError(null);
        try {
            const rawSq = values.similarQueries ?? [];
            const sqLines = rawSq
                .map((l) => (typeof l === "string" ? l.trim() : ""))
                .filter(Boolean)
                .slice(0, MAX_SIMILAR_QUERIES)
                .map((l) => (l.length > MAX_SIMILAR_QUERY_CHARS ? l.slice(0, MAX_SIMILAR_QUERY_CHARS) : l));
            const rows = (values.toolBindingRows ?? [])
                .map((r) => ({
                    placeholder: typeof r?.placeholder === "string" ? r.placeholder.trim() : "",
                    toolId: typeof r?.toolId === "string" ? r.toolId.trim() : "",
                    jsonPath: typeof r?.jsonPath === "string" ? r.jsonPath.trim() : "",
                }))
                .filter((r) => r.placeholder && r.toolId && r.jsonPath);
            const toolOutputBindings =
                rows.length > 0 ? {mappings: rows.map((r) => ({...r}))} : undefined;
            const linked = (values.linkedToolIds ?? []).filter(Boolean);
            const doc = await ingestKbDocument(selectedCollectionId, {
                title: values.title,
                body: values.body,
                ...(sqLines.length > 0 ? {similarQueries: sqLines} : {}),
                ...(linked.length > 0 ? {linkedToolIds: linked} : {}),
                ...(toolOutputBindings != null ? {toolOutputBindings} : {}),
            });
            message.success(
                doc.status === "FAILED"
                    ? "文档已写入但向量化失败，请查看列表中的错误信息"
                    : "文档已写入并完成向量化",
            );
            ingestForm.resetFields();
            setDocuments((prev) => {
                const rest = prev.filter((d) => d.id !== doc.id);
                return [doc, ...rest];
            });
            setIngestDrawerOpen(false);
        } catch (e) {
            setError(e);
        } finally {
            setIngesting(false);
        }
    }

    async function openViewDocument(docId: string) {
        if (!selectedCollectionId) {
            return;
        }
        setViewDocOpen(true);
        setViewDocLoading(true);
        setViewDocDetail(null);
        setViewToolById({});
        setRenderedPreviewBody(null);
        setToolOutputsPreviewJson("{}");
        setError(null);
        try {
            const d = await getKbDocument(selectedCollectionId, docId);
            setViewDocDetail(d);
        } catch (e) {
            setError(e);
            setViewDocOpen(false);
        } finally {
            setViewDocLoading(false);
        }
    }

    async function runDocPlaceholderPreview() {
        if (!selectedCollectionId || !viewDocDetail?.id) {
            return;
        }
        let parsed: Record<string, unknown>;
        try {
            parsed = JSON.parse(toolOutputsPreviewJson || "{}") as Record<string, unknown>;
        } catch {
            message.error("toolOutputs 不是合法 JSON 对象");
            return;
        }
        setPreviewLoading(true);
        setError(null);
        try {
            const r = await renderKbDocumentBody(selectedCollectionId, viewDocDetail.id, {toolOutputs: parsed});
            setRenderedPreviewBody(r.renderedBody ?? "");
            message.success("已按绑定生成预览（未写入数据库）");
        } catch (e) {
            setError(e);
        } finally {
            setPreviewLoading(false);
        }
    }

    const insertToolMentionIntoBody = React.useCallback(
        (mentionToken: string) => {
            const cur = (ingestForm.getFieldValue("body") as string | undefined) ?? "";
            const token = `{{tool:${mentionToken}}}`;
            const next = cur.length === 0 || cur.endsWith("\n") ? `${cur}${token}` : `${cur}\n${token}`;
            ingestForm.setFieldsValue({body: next});
            void message.success("已插入工具引用，可在正文中移动或继续编辑");
        },
        [ingestForm],
    );

    async function onDeleteCollection(collectionId: string) {
        setDeletingCollectionId(collectionId);
        setError(null);
        try {
            const del = await deleteKbCollection(collectionId);
            const n = typeof del?.agentsPolicyUpdated === "number" ? del.agentsPolicyUpdated : 0;
            message.success(
                n > 0
                    ? `已删除集合；已更新 ${n} 个智能体的知识库策略`
                    : "已删除集合（级联文档与分片）",
            );
            if (selectedCollectionId === collectionId) {
                setSelectedCollectionId(undefined);
            }
            setCollections((prev) => prev.filter((c) => c.id !== collectionId));
        } catch (e) {
            setError(e);
        } finally {
            setDeletingCollectionId(null);
        }
    }

    async function onDeleteDocument(documentId: string) {
        if (!selectedCollectionId) {
            return;
        }
        setDeletingDocumentId(documentId);
        setError(null);
        try {
            await deleteKbDocument(selectedCollectionId, documentId);
            message.success("已删除文档");
            setDocuments((prev) => prev.filter((d) => d.id !== documentId));
        } catch (e) {
            setError(e);
        } finally {
            setDeletingDocumentId(null);
        }
    }

    const docColumns = [
        {
            title: "标题",
            dataIndex: "title" as const,
            ellipsis: true,
            render: (t: string, row: KbDocumentDto) => (
                <Tooltip title={row.id}>
                    <Typography.Text strong>{t}</Typography.Text>
                </Tooltip>
            ),
        },
        {
            title: "状态",
            dataIndex: "status" as const,
            width: 100,
            render: (s: string) => documentStatusTag(s),
        },
        {
            title: "工具",
            key: "tools",
            width: 72,
            render: (_: unknown, row: KbDocumentDto) => (
                <Tag>{(row.linkedToolIds ?? []).length}</Tag>
            ),
        },
        {
            title: "错误信息",
            dataIndex: "errorMessage" as const,
            ellipsis: true,
            render: (t: string | undefined) =>
                t ? (
                    <Typography.Text type="danger" style={{fontSize: 13}}>
                        {t}
                    </Typography.Text>
                ) : (
                    "—"
                ),
        },
        {
            title: "创建时间",
            dataIndex: "createdAt" as const,
            width: 156,
            render: (v: string | undefined) => formatDateTime(v),
        },
        {
            title: "操作",
            key: "actions",
            width: 132,
            fixed: "right" as const,
            render: (_: unknown, row: KbDocumentDto) => (
                <Space size={0} wrap={false}>
                    <Button
                        type="link"
                        size="small"
                        icon={<EyeOutlined/>}
                        disabled={!selectedCollectionId}
                        onClick={() => void openViewDocument(row.id)}
                    >
                        查看
                    </Button>
                    <Popconfirm
                        title="删除该文档？"
                        description="将删除文档及其向量分片，不可恢复。"
                        okText="删除"
                        cancelText="取消"
                        okButtonProps={{danger: true}}
                        onConfirm={() => void onDeleteDocument(row.id)}
                    >
                        <Button
                            type="link"
                            danger
                            size="small"
                            icon={<DeleteOutlined/>}
                            disabled={!selectedCollectionId}
                            loading={deletingDocumentId === row.id}
                        />
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    return (
        <AppLayout>
            <Space orientation="vertical" size={16} style={{width: "100%"}}>
                <PageHeaderBlock
                    icon={<BookOutlined/>}
                    title="知识库"
                    subtitle="按「集合」隔离语料与向量空间；绑定文本嵌入模型后写入文档即可分片入库。在智能体中通过 knowledge_base_policy 引用集合以启用检索增强（RAG）。"
                    extra={
                        <Button
                            icon={<ReloadOutlined/>}
                            loading={refreshing}
                            onClick={() => void reloadBootstrap({toast: true})}
                        >
                            刷新
                        </Button>
                    }
                />

                <ErrorAlert error={error}/>

                <Row gutter={[16, 16]}>
                    <Col xs={24} lg={7}>
                        <div
                            style={{
                                background: "white",
                                border: "1px solid #e9eef5",
                                borderRadius: 12,
                                overflow: "hidden",
                                minHeight: 420,
                                display: "flex",
                                flexDirection: "column",
                            }}
                        >
                            <div
                                style={{
                                    padding: "14px 16px",
                                    borderBottom: "1px solid #f0f2f5",
                                    display: "flex",
                                    alignItems: "center",
                                    justifyContent: "space-between",
                                    gap: 8,
                                }}
                            >
                                <Typography.Text strong>知识集合</Typography.Text>
                                <Button type="primary" size="small" icon={<PlusOutlined/>}
                                        onClick={() => setCreateModalOpen(true)}>
                                    新建
                                </Button>
                            </div>
                            <div style={{padding: 12}}>
                                <Input
                                    allowClear
                                    prefix={<SearchOutlined style={{color: "rgba(0,0,0,0.35)"}}/>}
                                    placeholder="按名称、ID、模型搜索"
                                    value={collectionQuery}
                                    onChange={(e) => setCollectionQuery(e.target.value)}
                                />
                            </div>
                            <div style={{flex: 1, minHeight: 280, overflow: "auto"}}>
                                <List<KbCollectionDto>
                                    loading={loadingCollections}
                                    locale={{
                                        emptyText: (
                                            <Empty
                                                image={Empty.PRESENTED_IMAGE_SIMPLE}
                                                description="暂无集合"
                                            >
                                                <Button type="primary" size="small"
                                                        onClick={() => setCreateModalOpen(true)}>
                                                    创建第一个集合
                                                </Button>
                                            </Empty>
                                        ),
                                    }}
                                    dataSource={filteredCollections}
                                    renderItem={(item) => {
                                        const active = item.id === selectedCollectionId;
                                        return (
                                            <List.Item
                                                style={{
                                                    cursor: "pointer",
                                                    padding: "10px 16px",
                                                    borderLeft: active ? "3px solid #1677ff" : "3px solid transparent",
                                                    background: active ? "rgba(22,119,255,0.06)" : undefined,
                                                }}
                                                onClick={() => setSelectedCollectionId(item.id)}
                                                actions={[
                                                    <Popconfirm
                                                        key="del"
                                                        title="删除整个集合？"
                                                        description="级联删除文档与向量，并从智能体策略中移除引用。"
                                                        okText="删除"
                                                        cancelText="取消"
                                                        okButtonProps={{danger: true}}
                                                        onConfirm={(e) => {
                                                            e?.stopPropagation();
                                                            void onDeleteCollection(item.id);
                                                        }}
                                                    >
                                                        <Button
                                                            type="text"
                                                            danger
                                                            size="small"
                                                            icon={<DeleteOutlined/>}
                                                            loading={deletingCollectionId === item.id}
                                                            onClick={(e) => e.stopPropagation()}
                                                        />
                                                    </Popconfirm>,
                                                ]}
                                            >
                                                <List.Item.Meta
                                                    title={
                                                        <Typography.Text ellipsis style={{maxWidth: "100%"}}>
                                                            {item.name}
                                                        </Typography.Text>
                                                    }
                                                    description={
                                                        <Space orientation="vertical" size={2} style={{width: "100%"}}>
                                                            <Typography.Text type="secondary" style={{fontSize: 12}}
                                                                             ellipsis>
                                                                ID {item.id}
                                                            </Typography.Text>
                                                            <Space size={4} wrap>
                                                                <Tag color="blue">{item.embeddingDims ?? "—"} 维</Tag>
                                                                {item.chunkStrategy ? (
                                                                    <Tag color="geekblue">
                                                                        {chunkStrategyLabel(chunkMeta, item.chunkStrategy)}
                                                                    </Tag>
                                                                ) : null}
                                                            </Space>
                                                        </Space>
                                                    }
                                                />
                                            </List.Item>
                                        );
                                    }}
                                />
                            </div>
                        </div>
                    </Col>

                    <Col xs={24} lg={17}>
                        <div
                            style={{
                                background: "white",
                                border: "1px solid #e9eef5",
                                borderRadius: 12,
                                overflow: "hidden",
                            }}
                        >
                            <div
                                style={{
                                    padding: "14px 16px",
                                    borderBottom: "1px solid #f0f2f5",
                                    display: "flex",
                                    flexWrap: "wrap",
                                    alignItems: "center",
                                    justifyContent: "space-between",
                                    gap: 12,
                                }}
                            >
                                <div style={{minWidth: 0}}>
                                    <Typography.Title level={5} style={{margin: 0}}>
                                        {selectedCollection ? selectedCollection.name : "文档与内容"}
                                    </Typography.Title>
                                    <Typography.Text type="secondary" style={{fontSize: 13}}>
                                        {selectedCollection
                                            ? `当前集合 · ${documents.length} 个文档 · 分片：${chunkStrategyLabel(chunkMeta, selectedCollection.chunkStrategy)}`
                                            : "请从左侧选择一个集合以管理文档"}
                                    </Typography.Text>
                                </div>
                                <Button
                                    type="primary"
                                    icon={<FileAddOutlined/>}
                                    disabled={!selectedCollectionId}
                                    onClick={() => setIngestDrawerOpen(true)}
                                >
                                    新增文档
                                </Button>
                            </div>

                            {selectedCollection ? (
                                <div style={{padding: 16}}>
                                    <Alert
                                        type="info"
                                        showIcon
                                        style={{marginBottom: 16}}
                                        message="写入说明"
                                        description="提交后服务端会同步完成分片与向量化；大正文请控制在平台配置的字节/分片上限内。失败时文档仍会落库，状态为失败并附带错误原因。"
                                    />
                                    {selectedCollection.chunkParams && Object.keys(selectedCollection.chunkParams).length > 0 ? (
                                        <Alert
                                            type="success"
                                            showIcon
                                            style={{marginBottom: 16}}
                                            message="当前集合分片参数"
                                            description={
                                                <Typography.Text code copyable
                                                                 style={{fontSize: 12, whiteSpace: "pre-wrap"}}>
                                                    {JSON.stringify(selectedCollection.chunkParams, null, 2)}
                                                </Typography.Text>
                                            }
                                        />
                                    ) : null}
                                    <Row gutter={16} style={{marginBottom: 16}}>
                                        <Col span={8}>
                                            <Statistic title="集合内文档数" value={documents.length}/>
                                        </Col>
                                        <Col span={8}>
                                            <Statistic
                                                title="向量维度"
                                                value={selectedCollection.embeddingDims ?? "—"}
                                            />
                                        </Col>
                                        <Col span={8}>
                                            <Statistic
                                                title="嵌入模型 ID"
                                                valueRender={() => (
                                                    <Tooltip title={selectedCollection.embeddingModelId}>
                                                        <Typography.Text
                                                            ellipsis
                                                            style={{maxWidth: "100%", display: "block"}}
                                                            copyable={{text: selectedCollection.embeddingModelId}}
                                                        >
                                                            {selectedCollection.embeddingModelId}
                                                        </Typography.Text>
                                                    </Tooltip>
                                                )}
                                            />
                                        </Col>
                                    </Row>
                                    <Table<KbDocumentDto>
                                        size="middle"
                                        rowKey="id"
                                        loading={loadingDocs}
                                        dataSource={documents}
                                        pagination={tablePaginationFriendly()}
                                        columns={docColumns}
                                        scroll={{x: 860}}
                                    />
                                </div>
                            ) : (
                                <div style={{padding: 48}}>
                                    <Empty
                                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                                        description={
                                            <Space orientation="vertical" size={8}>
                                                <Typography.Text>未选择知识集合</Typography.Text>
                                                <Typography.Text type="secondary">
                                                    在左侧列表中点击集合名称，即可查看文档、写入语料并维护向量索引。
                                                </Typography.Text>
                                            </Space>
                                        }
                                    />
                                </div>
                            )}
                        </div>
                    </Col>
                </Row>

                <Modal
                    title="新建知识集合"
                    open={createModalOpen}
                    onCancel={() => setCreateModalOpen(false)}
                    footer={null}
                    destroyOnHidden
                    width={560}
                    afterOpenChange={(open) => {
                        if (!open) {
                            collectionForm.resetFields();
                            return;
                        }
                        const first = chunkMeta[0];
                        const fixed = chunkMeta.find((m) => m.value === "FIXED_WINDOW") ?? first;
                        const p = fixed?.defaultParams ?? {};
                        collectionForm.setFieldsValue({
                            chunkStrategy: fixed?.value ?? "FIXED_WINDOW",
                            maxChars: typeof p.maxChars === "number" ? p.maxChars : 900,
                            overlap: typeof p.overlap === "number" ? p.overlap : 120,
                            headingLevel: typeof p.headingLevel === "number" ? p.headingLevel : 2,
                            leadMaxChars: typeof p.leadMaxChars === "number" ? p.leadMaxChars : 512,
                        });
                    }}
                >
                    {embeddingModelRows.length === 0 ? (
                        <Alert
                            type="warning"
                            showIcon
                            style={{marginBottom: 16}}
                            message="暂无可用的文本嵌入模型配置"
                            description="请先在「模型」中创建 embedding 类型（非聊天）的模型配置，再创建知识集合。"
                        />
                    ) : null}
                    <Form
                        form={collectionForm}
                        layout="vertical"
                        onFinish={onCreateCollection}
                        initialValues={{
                            chunkStrategy: "FIXED_WINDOW",
                            maxChars: 900,
                            overlap: 120,
                            headingLevel: 2,
                            leadMaxChars: 512,
                        }}
                    >
                        <Form.Item name="name" label="集合名称" rules={[{required: true, message: "请输入名称"}]}>
                            <Input placeholder="例如 产品说明、内部制度"/>
                        </Form.Item>
                        <Form.Item name="description" label="描述（可选）">
                            <Input placeholder="便于他人理解该集合用途"/>
                        </Form.Item>
                        <Form.Item
                            name="chunkStrategy"
                            label="分片策略"
                            rules={[{required: true, message: "请选择分片策略"}]}
                            extra="创建后不可修改；不同策略影响检索粒度与召回行为。"
                        >
                            <Select
                                placeholder="选择分片策略"
                                options={chunkMeta.map((m) => ({
                                    label: m.label,
                                    value: m.value,
                                    title: m.description,
                                }))}
                                onChange={(v) => applyChunkStrategyDefaults(String(v))}
                            />
                        </Form.Item>
                        <Form.Item shouldUpdate={(prev, cur) => prev.chunkStrategy !== cur.chunkStrategy} noStyle>
                            {() => {
                                const s = collectionForm.getFieldValue("chunkStrategy") as string | undefined;
                                const m = chunkMeta.find((x) => x.value === s);
                                return m ? (
                                    <Typography.Paragraph type="secondary" style={{marginBottom: 12, marginTop: -4}}>
                                        {m.description}
                                    </Typography.Paragraph>
                                ) : null;
                            }}
                        </Form.Item>
                        <Form.Item
                            label="maxChars"
                            required
                            extra="单条分片最大字符数，范围 128～8192（与嵌入模型上下文相关）。"
                        >
                            <Space align="center" wrap>
                                <Form.Item
                                    name="maxChars"
                                    noStyle
                                    rules={[
                                        {required: true, message: "请输入 maxChars"},
                                        {
                                            type: "number",
                                            min: 128,
                                            max: 8192,
                                            message: "须在 128～8192 之间",
                                        },
                                    ]}
                                >
                                    <InputNumber min={128} max={8192} style={{width: 140}}/>
                                </Form.Item>
                                <Typography.Text type="secondary">overlap</Typography.Text>
                                <Form.Item
                                    name="overlap"
                                    noStyle
                                    rules={[
                                        {required: true, message: "请输入 overlap"},
                                        {type: "number", min: 0, message: "不能为负"},
                                    ]}
                                >
                                    <InputNumber min={0} max={4096} style={{width: 120}}/>
                                </Form.Item>
                            </Space>
                        </Form.Item>
                        <Form.Item noStyle shouldUpdate>
                            {() =>
                                collectionForm.getFieldValue("chunkStrategy") === "HEADING_SECTION" ? (
                                    <Space align="start" wrap style={{width: "100%", marginBottom: 12}}>
                                        <Form.Item
                                            name="headingLevel"
                                            label="标题级别"
                                            extra="1=按 # 切节；2=按 # 再按 ## 切小节。"
                                            rules={[
                                                {required: true, message: "请选择标题级别"},
                                                {
                                                    type: "number",
                                                    min: 1,
                                                    max: 2,
                                                    message: "仅支持 1 或 2",
                                                },
                                            ]}
                                        >
                                            <InputNumber min={1} max={2} style={{width: 120}}/>
                                        </Form.Item>
                                        <Form.Item
                                            name="leadMaxChars"
                                            label="引导段 maxChars"
                                            extra="用于向量拼接的引导正文长度，64～8192。"
                                            rules={[
                                                {required: true, message: "请输入 leadMaxChars"},
                                                {
                                                    type: "number",
                                                    min: 64,
                                                    max: 8192,
                                                    message: "须在 64～8192 之间",
                                                },
                                            ]}
                                        >
                                            <InputNumber min={64} max={8192} style={{width: 140}}/>
                                        </Form.Item>
                                    </Space>
                                ) : null
                            }
                        </Form.Item>
                        <Form.Item
                            name="embeddingModelId"
                            label="向量化模型配置"
                            rules={[{required: true, message: "请选择嵌入模型"}]}
                            extra="创建后不可更换嵌入模型；不同维度对应不同向量空间。"
                        >
                            <Select
                                showSearch
                                placeholder="选择平台中的 embedding 模型配置"
                                options={toModelSelectOptions(embeddingModelRows)}
                                popupMatchSelectWidth={520}
                                filterOption={(input, option) => {
                                    const st = (option as { searchText?: string }).searchText ?? "";
                                    const q = input.trim().toLowerCase();
                                    return !q || st.includes(q);
                                }}
                            />
                        </Form.Item>
                        <Form.Item style={{marginBottom: 0, textAlign: "right"}}>
                            <Space>
                                <Button onClick={() => setCreateModalOpen(false)}>取消</Button>
                                <Button type="primary" htmlType="submit" loading={creating}>
                                    创建
                                </Button>
                            </Space>
                        </Form.Item>
                    </Form>
                </Modal>

                <Drawer
                    title="新增文档"
                    width={920}
                    open={ingestDrawerOpen}
                    onClose={() => setIngestDrawerOpen(false)}
                    destroyOnHidden
                    styles={{body: {paddingBottom: 24}}}
                    afterOpenChange={(open) => {
                        if (open) {
                            ingestForm.resetFields();
                        }
                    }}
                    extra={
                        <Space>
                            <Button onClick={() => setIngestDrawerOpen(false)}>取消</Button>
                            <Button type="primary" loading={ingesting} onClick={() => void ingestForm.submit()}>
                                提交写入
                            </Button>
                        </Space>
                    }
                >
                    <Typography.Paragraph type="secondary" style={{marginTop: 0}}>
                        目标集合：
                        <Typography.Text strong>{selectedCollection?.name ?? "—"}</Typography.Text>
                    </Typography.Paragraph>
                    <Form
                        form={ingestForm}
                        layout="vertical"
                        onFinish={onIngest}
                        initialValues={{
                            similarQueries: [],
                            linkedToolIds: [],
                            toolBindingRows: [],
                        }}
                    >
                        <Form.Item name="title" label="标题" rules={[{required: true, message: "请输入标题"}]}>
                            <Input placeholder="在列表中展示的文档标题"/>
                        </Form.Item>
                        <Card size="small" title="工具与占位符" style={{marginBottom: 16}}>
                            <Typography.Paragraph type="secondary" style={{marginTop: 0}}>
                                先勾选<strong>关联工具</strong>，再在正文写{" "}
                                <Typography.Text code>{"{{tool:运行时名称}}"}</Typography.Text>
                                （与工具管理里的「名称」一致）。需要把工具 JSON 出参塞进正文时，用下方映射表配置{" "}
                                <Typography.Text code>{"{{占位符}}"}</Typography.Text>。
                            </Typography.Paragraph>
                            <Form.Item
                                name="linkedToolIds"
                                label="关联工具"
                                extra="正文里的 {{tool:…}} 与出参映射中的工具都必须包含在本列表中。支持按名称、展示名、类型、ID 搜索。"
                            >
                                <Select
                                    mode="multiple"
                                    allowClear
                                    loading={loadingToolPicker}
                                    options={toolSelectOptions}
                                    placeholder={loadingToolPicker ? "正在加载工具列表…" : "搜索并选择工具（可多选）"}
                                    showSearch
                                    filterOption={(input, option) => {
                                        const q = input.trim().toLowerCase();
                                        if (!q) {
                                            return true;
                                        }
                                        const id = String(option?.value ?? "");
                                        const t = toolById.get(id);
                                        return t ? kbToolSearchBlob(t).includes(q) : false;
                                    }}
                                    popupMatchSelectWidth={520}
                                    maxTagCount="responsive"
                                    tagRender={(props) => {
                                        const {value, closable, onClose} = props;
                                        const id = String(value);
                                        const t = toolById.get(id);
                                        const text = t ? kbToolChipText(t) : `未加载 ${id.slice(0, 8)}…`;
                                        return (
                                            <Tooltip title={t ? `${kbToolPrimaryLabel(t)} · ${id}` : id}>
                                                <Tag closable={closable} onClose={onClose} style={{marginInlineEnd: 4}}>
                                                    {text}
                                                </Tag>
                                            </Tooltip>
                                        );
                                    }}
                                />
                            </Form.Item>
                            <Form.Item shouldUpdate={(p, c) => p.linkedToolIds !== c.linkedToolIds} noStyle>
                                {() => {
                                    const linked = (ingestForm.getFieldValue("linkedToolIds") as string[] | undefined) ?? [];
                                    const linkedIds = linked.filter(Boolean);
                                    const menuItems = linkedIds.map((id) => {
                                        const t = toolById.get(id);
                                        const lab = t ? kbToolPrimaryLabel(t) : id.slice(0, 12) + "…";
                                        const mention = t?.name?.trim() ? t.name.trim() : id;
                                        return {key: id, label: lab, onClick: () => insertToolMentionIntoBody(mention)};
                                    });
                                    return (
                                        <Form.Item label="插入正文引用" style={{marginBottom: 12}}>
                                            {linkedIds.length === 0 ? (
                                                <Typography.Text
                                                    type="secondary">请先在上方关联至少一个工具。</Typography.Text>
                                            ) : (
                                                <Space orientation="vertical" size={8} style={{width: "100%"}}>
                                                    <Space wrap size={[8, 8]}>
                                                        {linkedIds.map((id) => {
                                                            const t = toolById.get(id);
                                                            const mention = t?.name?.trim() ? t.name.trim() : id;
                                                            const btnLabel = t ? kbToolChipText(t, 18) : id.slice(0, 8) + "…";
                                                            return (
                                                                <Tooltip
                                                                    key={id}
                                                                    title={`插入 {{tool:${mention}}}${t ? ` · ${kbToolOptionDescription(t)}` : ""}`}
                                                                >
                                                                    <Button
                                                                        size="small"
                                                                        type="default"
                                                                        onClick={() => insertToolMentionIntoBody(mention)}
                                                                    >
                                                                        + {btnLabel}
                                                                    </Button>
                                                                </Tooltip>
                                                            );
                                                        })}
                                                    </Space>
                                                    <Dropdown
                                                        menu={{items: menuItems}}
                                                        trigger={["click"]}
                                                    >
                                                        <Button size="small" icon={<DownOutlined/>}>
                                                            下拉选择插入（同上）
                                                        </Button>
                                                    </Dropdown>
                                                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                                                        引用会追加到正文末尾，再在编辑器里拖到合适位置即可。
                                                    </Typography.Text>
                                                </Space>
                                            )}
                                        </Form.Item>
                                    );
                                }}
                            </Form.Item>
                            <Form.Item
                                label="出参 → 正文占位符"
                                extra="每行：占位符 key（正文写 {{key}}）、工具、JSON 路径（如 $.data.orderNo）。仅完整填写才会提交。取消关联某工具时，对应行的工具选择会自动清空。"
                            >
                                <Form.List name="toolBindingRows">
                                    {(fields, {add, remove}) => (
                                        <Space orientation="vertical" size={8} style={{width: "100%"}}>
                                            {fields.map((field) => (
                                                <Space.Compact key={field.key}
                                                               style={{width: "100%", flexWrap: "wrap"}}>
                                                    <Form.Item
                                                        name={[field.name, "placeholder"]}
                                                        noStyle
                                                        rules={[{max: 64, message: "最长 64"}]}
                                                    >
                                                        <Input placeholder="占位符 key" style={{width: 128}}/>
                                                    </Form.Item>
                                                    <Form.Item name={[field.name, "toolId"]} noStyle>
                                                        <Select
                                                            placeholder="选工具"
                                                            style={{minWidth: 240, maxWidth: 320, flex: 1}}
                                                            options={bindingToolSelectOptions}
                                                            showSearch
                                                            allowClear
                                                            optionFilterProp="label"
                                                            popupMatchSelectWidth={400}
                                                        />
                                                    </Form.Item>
                                                    <Form.Item
                                                        name={[field.name, "jsonPath"]}
                                                        noStyle
                                                        rules={[{max: 512, message: "路径过长"}]}
                                                    >
                                                        <Input placeholder="$.data.field"
                                                               style={{minWidth: 140, flex: 1}}/>
                                                    </Form.Item>
                                                    <Button
                                                        type="text"
                                                        danger
                                                        icon={<MinusCircleOutlined/>}
                                                        onClick={() => remove(field.name)}
                                                        aria-label="删除该行"
                                                    />
                                                </Space.Compact>
                                            ))}
                                            <Button type="dashed" icon={<PlusOutlined/>} onClick={() => add({})} block>
                                                添加出参映射
                                            </Button>
                                        </Space>
                                    )}
                                </Form.List>
                            </Form.Item>
                        </Card>
                        <Form.Item
                            name="body"
                            label="正文（Markdown）"
                            rules={[{required: true, message: "请输入正文"}]}
                            extra="可先插入工具引用再写说明文字；支持 Markdown。"
                        >
                            <KbMarkdownField height={400}/>
                        </Form.Item>
                        <Form.Item
                            label="相似问（可选）"
                            extra={`用户口语化问法，会拼进每条分片的向量化文本以提升召回。最多 ${MAX_SIMILAR_QUERIES} 条，每条最长 ${MAX_SIMILAR_QUERY_CHARS} 字符。`}
                        >
                            <Form.List name="similarQueries">
                                {(fields, {add, remove}) => (
                                    <Space orientation="vertical" size={10} style={{width: "100%"}}>
                                        {fields.map((field) => (
                                            <Space.Compact key={field.key} style={{width: "100%"}}>
                                                <Form.Item
                                                    {...field}
                                                    noStyle
                                                    rules={[
                                                        {
                                                            max: MAX_SIMILAR_QUERY_CHARS,
                                                            message: `单条不超过 ${MAX_SIMILAR_QUERY_CHARS} 字符`,
                                                        },
                                                    ]}
                                                >
                                                    <Input
                                                        placeholder="例如：怎么退款？会员能退吗？"
                                                        maxLength={MAX_SIMILAR_QUERY_CHARS}
                                                        showCount
                                                        style={{width: "calc(100% - 40px)"}}
                                                    />
                                                </Form.Item>
                                                <Button
                                                    type="text"
                                                    danger
                                                    icon={<MinusCircleOutlined/>}
                                                    onClick={() => remove(field.name)}
                                                    aria-label="删除该条"
                                                />
                                            </Space.Compact>
                                        ))}
                                        <Button
                                            type="dashed"
                                            icon={<PlusOutlined/>}
                                            onClick={() => add("")}
                                            disabled={fields.length >= MAX_SIMILAR_QUERIES}
                                            block
                                        >
                                            添加相似问（{fields.length}/{MAX_SIMILAR_QUERIES}）
                                        </Button>
                                    </Space>
                                )}
                            </Form.List>
                        </Form.Item>
                    </Form>
                </Drawer>

                <Drawer
                    title={viewDocDetail ? `文档：${viewDocDetail.title}` : "文档内容"}
                    width={920}
                    open={viewDocOpen}
                    onClose={() => {
                        setViewDocOpen(false);
                        setViewDocDetail(null);
                        setViewToolById({});
                    }}
                    destroyOnHidden
                    extra={
                        viewDocDetail?.body != null ? (
                            <Typography.Text copyable={{text: viewDocDetail.body}} type="secondary"
                                             style={{fontSize: 12}}>
                                复制原文
                            </Typography.Text>
                        ) : null
                    }
                >
                    <Spin spinning={viewDocLoading}>
                        {viewDocDetail ? (
                            <Space orientation="vertical" size={12} style={{width: "100%"}}>
                                <Space wrap align="center">
                                    {documentStatusTag(viewDocDetail.status)}
                                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                                        ID {viewDocDetail.id}
                                    </Typography.Text>
                                </Space>
                                {viewDocDetail.errorMessage ? (
                                    <Alert type="error" message={viewDocDetail.errorMessage} showIcon/>
                                ) : null}
                                {(viewDocDetail.linkedToolIds ?? []).length > 0 ? (
                                    <div>
                                        <Space align="center" style={{marginBottom: 6}}>
                                            <Typography.Text type="secondary" style={{fontSize: 12}}>
                                                已绑定工具（{viewDocDetail.linkedToolIds!.length}）
                                            </Typography.Text>
                                            {loadingViewTools ? <Spin size="small"/> : null}
                                        </Space>
                                        <Space wrap size={[6, 6]}>
                                            {viewDocDetail.linkedToolIds!.map((tid) => {
                                                const t = viewToolById[tid];
                                                const label = t ? kbToolPrimaryLabel(t) : tid;
                                                return (
                                                    <Tooltip key={tid} title={tid}>
                                                        <Tag color="blue">{label}</Tag>
                                                    </Tooltip>
                                                );
                                            })}
                                        </Space>
                                    </div>
                                ) : (
                                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                                        未绑定工具
                                    </Typography.Text>
                                )}
                                <Tabs
                                    items={[
                                        {
                                            key: "raw",
                                            label: "原文",
                                            children: (
                                                <Space orientation="vertical" size={8} style={{width: "100%"}}>
                                                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                                                        入库原文（Markdown）
                                                    </Typography.Text>
                                                    <KbMarkdownPreview source={viewDocDetail.body ?? "_（无正文）_"}/>
                                                </Space>
                                            ),
                                        },
                                        {
                                            key: "ph",
                                            label: "占位符预览",
                                            children: (
                                                <Space orientation="vertical" size={12} style={{width: "100%"}}>
                                                    <Typography.Paragraph type="secondary" style={{marginBottom: 0}}>
                                                        输入 toolOutputs（工具 ID → 该工具一次调用的 JSON 根对象）：先按本文档的
                                                        toolOutputBindings
                                                        替换 {"{{placeholder}}"}，再将 {"{{tool:名称}}"}（或数字
                                                        ID）展开为工具展示名；不入库。
                                                    </Typography.Paragraph>
                                                    <Input.TextArea
                                                        rows={10}
                                                        value={toolOutputsPreviewJson}
                                                        onChange={(e) => setToolOutputsPreviewJson(e.target.value)}
                                                        placeholder={'{\n  "1800000000001000001": { "data": { "orderNo": "A-1" } }\n}'}
                                                        style={{fontFamily: "monospace", fontSize: 12}}
                                                    />
                                                    <Button type="primary" loading={previewLoading}
                                                            onClick={() => void runDocPlaceholderPreview()}>
                                                        生成预览
                                                    </Button>
                                                    {renderedPreviewBody != null ? (
                                                        <>
                                                            <Typography.Text type="secondary" style={{fontSize: 12}}>
                                                                替换后预览
                                                            </Typography.Text>
                                                            <KbMarkdownPreview
                                                                source={renderedPreviewBody || "_（空）_"}/>
                                                        </>
                                                    ) : null}
                                                </Space>
                                            ),
                                        },
                                    ]}
                                />
                            </Space>
                        ) : !viewDocLoading ? (
                            <Empty description="暂无数据"/>
                        ) : null}
                    </Spin>
                </Drawer>
            </Space>
        </AppLayout>
    );
}
