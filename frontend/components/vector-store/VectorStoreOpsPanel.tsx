"use client";

import {
    DatabaseOutlined,
    DeleteOutlined,
    ExperimentOutlined,
    QuestionCircleOutlined,
    ReloadOutlined,
    ThunderboltOutlined,
    UnorderedListOutlined,
} from "@ant-design/icons";
import {
    Alert,
    Button,
    Card,
    Descriptions,
    Empty,
    Form,
    Input,
    message,
    Modal,
    Popover,
    Select,
    Space,
    Spin,
    Table,
    Tabs,
    Tag,
    Tooltip,
    Typography,
} from "antd";
import Link from "next/link";
import React from "react";

import {ErrorAlert} from "@/components/ErrorAlert";
import {modelNameForId, type ModelOptionRow} from "@/lib/model-select-options";
import {
    dropVectorStoreCollection,
    embeddingProbeVectorStore,
    getVectorStoreCollectionStats,
    getVectorStoreUsage,
    listVectorStoreCollections,
    listVectorStoreProfiles,
    loadVectorStoreCollection,
    previewVectorStorePoints,
    probeVectorStoreProfile,
} from "@/lib/vector-store/api";
import type {
    VectorStoreCollectionStatsDto,
    VectorStoreCollectionSummaryDto,
    VectorStoreEmbeddingProbeResultDto,
    VectorStorePointPreviewRowDto,
    VectorStoreProbeResultDto,
    VectorStoreProfileDto,
    VectorStoreUsageDto,
} from "@/lib/vector-store/types";

function maskConfig(raw?: Record<string, unknown>): Record<string, unknown> {
    if (!raw) {
        return {};
    }
    const out: Record<string, unknown> = {...raw};
    if (typeof out.token === "string" && out.token) {
        out.token = "***";
    }
    if (typeof out.password === "string" && out.password) {
        out.password = "***";
    }
    if (typeof out.apiKey === "string" && out.apiKey) {
        out.apiKey = "***";
    }
    return out;
}

export type VectorStoreOpsPanelProps = {
    /** 从 URL 预选中（仅非受控时生效） */
    initialProfileId?: string | null;
    /**
     * page：独立页（集合运维书签页）
     * drawer：旧版抽屉内嵌（保留兼容）
     * inline：与父页「主从布局」联动，通常配合 controlledProfileId
     */
    variant?: "page" | "drawer" | "inline";
    /** 父组件锁定当前连接：隐藏下拉，仅运维该 profile */
    controlledProfileId?: string | null;
    /** 取消选择（如返回仅列表视图） */
    onClearSelection?: () => void;
    /**
     * 由父页传入连接列表时，不再重复请求 /vector-store-profiles（主从页内联用）
     */
    externalProfiles?: VectorStoreProfileDto[];
    /** 与 externalProfiles 配套：父页列表 loading */
    profilesLoading?: boolean;
    /** 刷新列表（与 externalProfiles 配套） */
    onRefreshProfiles?: () => void | Promise<void>;
    /** 用于将 embeddingModelId 显示为模型配置名称（与「模型」页一致） */
    embeddingModelRows?: ModelOptionRow[];
};

export function VectorStoreOpsPanel({
                                        initialProfileId,
                                        variant = "page",
                                        controlledProfileId,
                                        onClearSelection,
                                        externalProfiles,
                                        profilesLoading: profilesLoadingProp,
                                        onRefreshProfiles,
                                        embeddingModelRows = [],
                                    }: VectorStoreOpsPanelProps) {
    const isCompact = variant === "drawer" || variant === "inline";
    const isControlled =
        controlledProfileId != null && String(controlledProfileId).length > 0;

    const parentOwnsProfileList = externalProfiles !== undefined;

    const [internalRows, setInternalRows] = React.useState<VectorStoreProfileDto[]>([]);
    const rows = parentOwnsProfileList ? externalProfiles! : internalRows;

    const [internalListLoading, setInternalListLoading] = React.useState(() => !parentOwnsProfileList);
    const listLoading =
        profilesLoadingProp !== undefined ? profilesLoadingProp : internalListLoading;
    const [error, setError] = React.useState<unknown>(null);
    const [internalSelectedId, setInternalSelectedId] = React.useState<string | undefined>(undefined);
    const selectedId = isControlled ? String(controlledProfileId) : internalSelectedId;

    const [probeResult, setProbeResult] = React.useState<VectorStoreProbeResultDto | null>(null);
    const [probing, setProbing] = React.useState(false);
    const [usage, setUsage] = React.useState<VectorStoreUsageDto | null>(null);
    const [usageLoading, setUsageLoading] = React.useState(false);
    const [collections, setCollections] = React.useState<VectorStoreCollectionSummaryDto[]>([]);
    const [collectionsLoading, setCollectionsLoading] = React.useState(false);
    const [statsOpen, setStatsOpen] = React.useState(false);
    const [statsData, setStatsData] = React.useState<VectorStoreCollectionStatsDto | null>(null);
    const [statsLoading, setStatsLoading] = React.useState(false);
    const [dropOpen, setDropOpen] = React.useState(false);
    const [dropTarget, setDropTarget] = React.useState("");
    const [dropForm] = Form.useForm<{ name: string; confirm: string }>();
    const [embedText, setEmbedText] = React.useState("测试向量维度是否与本配置一致");
    const [embedResult, setEmbedResult] = React.useState<VectorStoreEmbeddingProbeResultDto | null>(null);
    const [embedLoading, setEmbedLoading] = React.useState(false);

    const [pointsPreviewOpen, setPointsPreviewOpen] = React.useState(false);
    const [pointsPreviewPid, setPointsPreviewPid] = React.useState("");
    const [pointsPreviewCollection, setPointsPreviewCollection] = React.useState("");
    const [pointsPreviewRows, setPointsPreviewRows] = React.useState<VectorStorePointPreviewRowDto[]>([]);
    const [pointsPreviewNext, setPointsPreviewNext] = React.useState<string | null>(null);
    const [pointsPreviewHint, setPointsPreviewHint] = React.useState<string | null>(null);
    const [pointsPreviewLoading, setPointsPreviewLoading] = React.useState(false);

    const opsProfile = React.useMemo(
        () => rows.find((r) => r.id === selectedId) ?? null,
        [rows, selectedId],
    );

    React.useEffect(() => {
        if (!isControlled && initialProfileId) {
            setInternalSelectedId(initialProfileId);
        }
    }, [initialProfileId, isControlled]);

    async function refreshProfiles() {
        if (parentOwnsProfileList) {
            setError(null);
            if (onRefreshProfiles) {
                try {
                    await onRefreshProfiles();
                } catch (e) {
                    setError(e);
                }
            }
            return;
        }
        setInternalListLoading(true);
        setError(null);
        try {
            const list = await listVectorStoreProfiles();
            setInternalRows(list);
        } catch (e) {
            setError(e);
            setInternalRows([]);
        } finally {
            setInternalListLoading(false);
        }
    }

    React.useEffect(() => {
        if (parentOwnsProfileList) {
            return;
        }
        void refreshProfiles();
        // eslint-disable-next-line react-hooks/exhaustive-deps -- 仅挂载时拉取；父级列表由 externalProfiles 注入
    }, []);

    async function loadUsage(pid: string) {
        setUsageLoading(true);
        try {
            const u = await getVectorStoreUsage(pid);
            setUsage(u);
        } catch (e) {
            setError(e);
        } finally {
            setUsageLoading(false);
        }
    }

    React.useEffect(() => {
        if (selectedId) {
            void loadUsage(selectedId);
            setProbeResult(null);
            setCollections([]);
            setEmbedResult(null);
        } else {
            setUsage(null);
        }
    }, [selectedId]);

    async function runProbe(pid: string) {
        setProbing(true);
        setProbeResult(null);
        try {
            const r = await probeVectorStoreProfile(pid);
            setProbeResult(r);
            if (r.ok) {
                message.success(`探测成功，耗时 ${r.latencyMs} ms`);
            } else {
                message.warning(r.message ?? "探测失败");
            }
        } catch (e) {
            setError(e);
        } finally {
            setProbing(false);
        }
    }

    async function refreshCollections(pid: string) {
        setCollectionsLoading(true);
        try {
            const list = await listVectorStoreCollections(pid);
            setCollections(list);
            message.success(`已加载 ${list.length} 个 collection`);
        } catch (e) {
            setError(e);
        } finally {
            setCollectionsLoading(false);
        }
    }

    async function showStats(pid: string, name: string) {
        setStatsOpen(true);
        setStatsData(null);
        setStatsLoading(true);
        try {
            const s = await getVectorStoreCollectionStats(pid, name);
            setStatsData(s);
        } catch (e) {
            setError(e);
            setStatsOpen(false);
        } finally {
            setStatsLoading(false);
        }
    }

    async function runLoad(pid: string, name: string) {
        try {
            await loadVectorStoreCollection(pid, name);
            message.success("已触发 loadCollection");
        } catch (e) {
            setError(e);
        }
    }

    function openDrop(name: string) {
        setDropTarget(name);
        setDropOpen(true);
        dropForm.setFieldsValue({name, confirm: ""});
    }

    async function runDrop(pid: string) {
        try {
            const v = await dropForm.validateFields();
            await dropVectorStoreCollection(pid, {
                collectionName: v.name.trim(),
                confirmCollectionName: v.confirm.trim(),
            });
            message.success("已删除物理 collection");
            setDropOpen(false);
            await refreshCollections(pid);
        } catch (e) {
            if (e && typeof e === "object" && "errorFields" in (e as object)) {
                return;
            }
            setError(e);
        }
    }

    async function runEmbeddingProbe(pid: string) {
        setEmbedLoading(true);
        setEmbedResult(null);
        try {
            const r = await embeddingProbeVectorStore(pid, embedText.trim() || "hi");
            setEmbedResult(r);
            if (r.ok && r.dimensionMatchesProfile) {
                message.success("嵌入维度与 profile 一致");
            } else {
                message.warning(r.message ?? "请检查维度");
            }
        } catch (e) {
            setError(e);
        } finally {
            setEmbedLoading(false);
        }
    }

    async function openPointsPreview(pid: string, collectionName: string) {
        setPointsPreviewOpen(true);
        setPointsPreviewPid(pid);
        setPointsPreviewCollection(collectionName);
        setPointsPreviewRows([]);
        setPointsPreviewNext(null);
        setPointsPreviewHint(null);
        await fetchPointsPreview(pid, collectionName, null, false);
    }

    async function fetchPointsPreview(
        pid: string,
        collectionName: string,
        cursor: string | null,
        append: boolean,
    ) {
        setPointsPreviewLoading(true);
        try {
            const d = await previewVectorStorePoints(pid, collectionName, {
                limit: 20,
                cursor: cursor ?? undefined,
            });
            setPointsPreviewHint(d.hint ?? null);
            if (append) {
                setPointsPreviewRows((prev) => [...prev, ...d.rows]);
            } else {
                setPointsPreviewRows(d.rows);
            }
            setPointsPreviewNext(d.nextCursor ?? null);
        } catch (e) {
            setError(e);
        } finally {
            setPointsPreviewLoading(false);
        }
    }

    const helpContent = (
        <div style={{maxWidth: 320}}>
            <Typography.Paragraph style={{marginBottom: 8}} strong>
                快速上手
            </Typography.Paragraph>
            <ol style={{margin: 0, paddingLeft: 18, lineHeight: 1.7}}>
                <li>选一条已保存的连接。</li>
                <li>「引用」看知识库占用了哪些物理 collection。</li>
                <li>「远程」里刷新列表 → 统计 / 数据预览（Qdrant）/ Load / 删表。</li>
            </ol>
            <Typography.Paragraph type="secondary" style={{margin: "12px 0 0", marginBottom: 0, fontSize: 12}}>
                每个物理 collection 至多绑定一个知识库集合。
            </Typography.Paragraph>
        </div>
    );

    const helpButton = (
        <Popover title="帮助" content={helpContent} trigger="click">
            <Button type="text" size="small" icon={<QuestionCircleOutlined/>}>
                帮助
            </Button>
        </Popover>
    );

    return (
        <div>
            <ErrorAlert error={error}/>

            {isControlled ? (
                <Card size="small" style={{marginBottom: 12}}>
                    <Space wrap align="center" style={{width: "100%"}}>
                        <Typography.Text strong>
                            {opsProfile?.name ?? (listLoading ? "加载中…" : "—")}
                        </Typography.Text>
                        {opsProfile ? <Tag>{opsProfile.vectorStoreKind}</Tag> : null}
                        <Button icon={<ReloadOutlined/>} size="small" onClick={() => void refreshProfiles()}
                                loading={listLoading}>
                            刷新
                        </Button>
                        {variant === "inline" ? helpButton : null}
                        {onClearSelection ? (
                            <Button type="link" size="small" onClick={onClearSelection}>
                                取消选择
                            </Button>
                        ) : null}
                    </Space>
                </Card>
            ) : (
                <Card
                    size="small"
                    title={isCompact ? "当前连接" : "连接"}
                    extra={variant === "page" ? helpButton : undefined}
                    style={{marginBottom: 12}}
                >
                    <Space wrap style={{width: "100%"}} align="center">
                        <Select
                            showSearch
                            allowClear
                            placeholder="选择连接…"
                            style={{minWidth: 280, flex: 1}}
                            loading={listLoading}
                            options={rows.map((r) => ({
                                label: `${r.name}（${r.vectorStoreKind}）`,
                                value: r.id,
                            }))}
                            value={selectedId}
                            onChange={(v) => setInternalSelectedId(v)}
                            optionFilterProp="label"
                        />
                        <Button icon={<ReloadOutlined/>} onClick={() => void refreshProfiles()} loading={listLoading}>
                            刷新
                        </Button>
                        {variant === "page" ? (
                            <Link href="/vector-store" style={{marginLeft: "auto"}}>
                                <Button type="link">向量库首页</Button>
                            </Link>
                        ) : null}
                    </Space>
                </Card>
            )}

            {!listLoading && rows.length === 0 ? (
                <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="暂无连接，请先在配置页新建"
                >
                    <Link href="/vector-store">
                        <Button type="primary">去配置</Button>
                    </Link>
                </Empty>
            ) : isControlled && !listLoading && rows.length > 0 && !opsProfile ? (
                <Alert
                    type="error"
                    showIcon
                    title="找不到该连接"
                    description="可能已删除，请刷新或取消选择。"
                    action={
                        onClearSelection ? (
                            <Button size="small" type="primary" ghost onClick={onClearSelection}>
                                取消选择
                            </Button>
                        ) : undefined
                    }
                />
            ) : !opsProfile ? (
                <Alert
                    type="warning"
                    showIcon
                    title="请选择一条连接"
                    description={isCompact ? undefined : "通过 ?profile= 可深链选中。"}
                />
            ) : (
                <Tabs
                    defaultActiveKey="ref"
                    items={[
                        {
                            key: "ref",
                            label: "引用",
                            children: (
                                <Space orientation="vertical" size={16} style={{width: "100%"}}>
                                    {isCompact ? (
                                        <Typography.Text type="secondary">
                                            只读：连接参数与平台侧占用。
                                        </Typography.Text>
                                    ) : null}
                                    <Descriptions bordered size="small" column={1}>
                                        <Descriptions.Item label="类型">{opsProfile.vectorStoreKind}</Descriptions.Item>
                                        <Descriptions.Item label="嵌入模型">
                                            <Space wrap size={4}>
                                                <Link
                                                    href={`/models/${encodeURIComponent(opsProfile.embeddingModelId)}`}>
                                                    <Typography.Text strong>
                                                        {modelNameForId(embeddingModelRows, opsProfile.embeddingModelId)}
                                                    </Typography.Text>
                                                </Link>
                                                <Typography.Text type="secondary" style={{fontSize: 12}}>
                                                    编号
                                                </Typography.Text>
                                                <Typography.Text
                                                    code
                                                    copyable={{text: opsProfile.embeddingModelId}}
                                                    style={{fontSize: 12}}
                                                >
                                                    {opsProfile.embeddingModelId.length > 24
                                                        ? `${opsProfile.embeddingModelId.slice(0, 22)}…`
                                                        : opsProfile.embeddingModelId}
                                                </Typography.Text>
                                            </Space>
                                        </Descriptions.Item>
                                        <Descriptions.Item
                                            label="声明维度">{opsProfile.embeddingDims}</Descriptions.Item>
                                        <Descriptions.Item label="连接（脱敏）">
                                            <pre
                                                style={{
                                                    margin: 0,
                                                    fontSize: 11,
                                                    whiteSpace: "pre-wrap",
                                                    maxHeight: isCompact ? 120 : 160,
                                                }}
                                            >
                                                {JSON.stringify(maskConfig(opsProfile.vectorStoreConfig), null, 2)}
                                            </pre>
                                        </Descriptions.Item>
                                    </Descriptions>

                                    <Card size="small" title="平台引用" loading={usageLoading}>
                                        {usage ? (
                                            <>
                                                <Typography.Paragraph style={{marginBottom: 8}}>
                                                    知识库集合 <Tag color="blue">{usage.kbCollectionCount}</Tag>
                                                </Typography.Paragraph>
                                                {usage.kbCollections.length > 0 ? (
                                                    <ul style={{margin: 0, paddingLeft: 18}}>
                                                        {usage.kbCollections.map((c) => (
                                                            <li key={c.id}>
                                                                <Link href="/kb">{c.name}</Link>
                                                                {c.physicalCollectionName ? (
                                                                    <>
                                                                        {" "}
                                                                        <Typography.Text type="secondary" code>
                                                                            {c.physicalCollectionName}
                                                                        </Typography.Text>
                                                                    </>
                                                                ) : null}{" "}
                                                                <Typography.Text type="secondary" code>
                                                                    {c.id.slice(0, 10)}…
                                                                </Typography.Text>
                                                            </li>
                                                        ))}
                                                    </ul>
                                                ) : (
                                                    <Typography.Text type="secondary">暂无知识库引用</Typography.Text>
                                                )}
                                            </>
                                        ) : (
                                            <Typography.Text type="secondary">加载中…</Typography.Text>
                                        )}
                                        <Tooltip title="重新拉取占用">
                                            <Button
                                                size="small"
                                                style={{marginTop: 8}}
                                                onClick={() => void loadUsage(opsProfile.id)}
                                            >
                                                刷新引用
                                            </Button>
                                        </Tooltip>
                                    </Card>
                                </Space>
                            ),
                        },
                        {
                            key: "ops",
                            label: "远程",
                            children: (
                                <Space orientation="vertical" size={16} style={{width: "100%"}}>
                                    <Card
                                        size="small"
                                        title={
                                            <Space>
                                                <ThunderboltOutlined/>
                                                探测
                                            </Space>
                                        }
                                        extra={
                                            <Button
                                                size="small"
                                                type="primary"
                                                loading={probing}
                                                onClick={() => void runProbe(opsProfile.id)}
                                            >
                                                探测
                                            </Button>
                                        }
                                    >
                                        {probeResult ? (
                                            <Alert
                                                type={probeResult.ok ? "success" : "error"}
                                                showIcon
                                                title={probeResult.ok ? "成功" : "失败"}
                                                description={
                                                    <div>
                                                        <div>{probeResult.message}</div>
                                                        <div style={{marginTop: 8, fontSize: 12}}>
                                                            {probeResult.latencyMs} ms
                                                            {probeResult.serverVersion ? ` · ${probeResult.serverVersion}` : ""}
                                                            {probeResult.collectionCount != null
                                                                ? ` · collections: ${probeResult.collectionCount}`
                                                                : ""}
                                                        </div>
                                                        {probeResult.healthSummary ? (
                                                            <div style={{marginTop: 4, fontSize: 12}}>
                                                                {probeResult.healthSummary}
                                                            </div>
                                                        ) : null}
                                                    </div>
                                                }
                                            />
                                        ) : (
                                            <Typography.Text type="secondary">检查连通性</Typography.Text>
                                        )}
                                    </Card>

                                    <Card
                                        size="small"
                                        title={
                                            <Space>
                                                <UnorderedListOutlined/>
                                                远程表
                                            </Space>
                                        }
                                        extra={
                                            <Button
                                                size="small"
                                                icon={<ReloadOutlined/>}
                                                loading={collectionsLoading}
                                                onClick={() => void refreshCollections(opsProfile.id)}
                                            >
                                                刷新列表
                                            </Button>
                                        }
                                    >
                                        <Table<VectorStoreCollectionSummaryDto>
                                            size="small"
                                            rowKey="name"
                                            dataSource={collections}
                                            pagination={false}
                                            locale={{emptyText: "点「刷新列表」"}}
                                            columns={[
                                                {title: "名称", dataIndex: "name", ellipsis: true},
                                                {
                                                    title: "%",
                                                    width: 56,
                                                    render: (_, row) =>
                                                        row.loadedPercent != null ? `${row.loadedPercent}` : "—",
                                                },
                                                {
                                                    title: "",
                                                    width: 300,
                                                    render: (_, row) => (
                                                        <Space wrap size={4}>
                                                            <Button
                                                                size="small"
                                                                onClick={() => void showStats(opsProfile.id, row.name)}
                                                            >
                                                                统计
                                                            </Button>
                                                            <Tooltip title="Qdrant：scroll 预览 payload；Milvus 见提示">
                                                                <Button
                                                                    size="small"
                                                                    icon={<DatabaseOutlined/>}
                                                                    onClick={() => void openPointsPreview(opsProfile.id, row.name)}
                                                                >
                                                                    数据
                                                                </Button>
                                                            </Tooltip>
                                                            {opsProfile.vectorStoreKind === "MILVUS" ? (
                                                                <Button
                                                                    size="small"
                                                                    onClick={() => void runLoad(opsProfile.id, row.name)}
                                                                >
                                                                    Load
                                                                </Button>
                                                            ) : null}
                                                            <Button
                                                                size="small"
                                                                danger
                                                                icon={<DeleteOutlined/>}
                                                                onClick={() => openDrop(row.name)}
                                                            >
                                                                删除
                                                            </Button>
                                                        </Space>
                                                    ),
                                                },
                                            ]}
                                        />
                                    </Card>

                                    <Card
                                        size="small"
                                        title={
                                            <Space>
                                                <ExperimentOutlined/>
                                                嵌入自检
                                            </Space>
                                        }
                                        extra={
                                            <Button
                                                size="small"
                                                type="primary"
                                                loading={embedLoading}
                                                onClick={() => void runEmbeddingProbe(opsProfile.id)}
                                            >
                                                运行
                                            </Button>
                                        }
                                    >
                                        <Input.TextArea
                                            rows={isCompact ? 2 : 3}
                                            value={embedText}
                                            onChange={(e) => setEmbedText(e.target.value)}
                                            placeholder="试算文本"
                                        />
                                        {embedResult ? (
                                            <Alert
                                                style={{marginTop: 12}}
                                                type={
                                                    embedResult.ok && embedResult.dimensionMatchesProfile
                                                        ? "success"
                                                        : "warning"
                                                }
                                                showIcon
                                                title={embedResult.message}
                                                description={
                                                    <div style={{fontSize: 12}}>
                                                        dim {embedResult.vectorDimension} · 与 profile 一致：
                                                        {embedResult.dimensionMatchesProfile ? "是" : "否"}
                                                    </div>
                                                }
                                            />
                                        ) : null}
                                    </Card>
                                </Space>
                            ),
                        },
                    ]}
                />
            )}

            <Modal
                title={`统计：${statsData?.collectionName ?? ""}`}
                open={statsOpen}
                onCancel={() => setStatsOpen(false)}
                footer={null}
                destroyOnHidden
            >
                {statsLoading ? (
                    <Typography.Text type="secondary">加载中…</Typography.Text>
                ) : statsData ? (
                    <Space orientation="vertical" style={{width: "100%"}}>
                        <div>
                            条数：<strong>{statsData.rowCount ?? "—"}</strong>
                        </div>
                        {statsData.rawStats && Object.keys(statsData.rawStats).length > 0 ? (
                            <pre style={{fontSize: 12, margin: 0, whiteSpace: "pre-wrap"}}>
                                {JSON.stringify(statsData.rawStats, null, 2)}
                            </pre>
                        ) : null}
                    </Space>
                ) : null}
            </Modal>

            <Modal
                title={`点数据预览 · ${pointsPreviewCollection}`}
                open={pointsPreviewOpen}
                onCancel={() => setPointsPreviewOpen(false)}
                footer={null}
                width={900}
                destroyOnHidden
            >
                {pointsPreviewHint ? (
                    <Alert type="info" showIcon style={{marginBottom: 12}} title={pointsPreviewHint}/>
                ) : null}
                <Spin spinning={pointsPreviewLoading}>
                    <Table<VectorStorePointPreviewRowDto>
                        size="small"
                        rowKey={(_, i) => `${pointsPreviewCollection}-${i}`}
                        dataSource={pointsPreviewRows}
                        pagination={false}
                        scroll={{x: 700, y: 400}}
                        locale={{emptyText: pointsPreviewLoading ? "加载中…" : "暂无数据"}}
                        columns={[
                            {
                                title: "ID",
                                dataIndex: "id",
                                width: 140,
                                ellipsis: true,
                            },
                            {
                                title: "Payload",
                                dataIndex: "payload",
                                render: (p: Record<string, unknown> | undefined) => (
                                    <pre
                                        style={{
                                            margin: 0,
                                            fontSize: 11,
                                            whiteSpace: "pre-wrap",
                                            wordBreak: "break-word",
                                            maxHeight: 220,
                                            overflow: "auto",
                                        }}
                                    >
                                        {p && Object.keys(p).length > 0 ? JSON.stringify(p, null, 2) : "—"}
                                    </pre>
                                ),
                            },
                        ]}
                    />
                </Spin>
                {pointsPreviewNext ? (
                    <div style={{marginTop: 12}}>
                        <Button
                            loading={pointsPreviewLoading}
                            onClick={() =>
                                void fetchPointsPreview(
                                    pointsPreviewPid,
                                    pointsPreviewCollection,
                                    pointsPreviewNext,
                                    true,
                                )
                            }
                        >
                            加载更多
                        </Button>
                    </div>
                ) : null}
            </Modal>

            <Modal
                title="删除远程 collection"
                open={dropOpen}
                onCancel={() => setDropOpen(false)}
                onOk={() => opsProfile && void runDrop(opsProfile.id)}
                okText="删除"
                okButtonProps={{danger: true}}
                destroyOnHidden
            >
                <Alert
                    type="error"
                    showIcon
                    style={{marginBottom: 12}}
                    title="不可恢复"
                />
                <Typography.Paragraph>
                    <Typography.Text code>{dropTarget}</Typography.Text>
                </Typography.Paragraph>
                <Form form={dropForm} layout="vertical">
                    <Form.Item name="name" label="名称" rules={[{required: true, message: "必填"}]}>
                        <Input/>
                    </Form.Item>
                    <Form.Item
                        name="confirm"
                        label="确认"
                        rules={[
                            {required: true, message: "必填"},
                            {
                                validator: async (_, v) => {
                                    if ((v ?? "").trim() !== (dropTarget ?? "").trim()) {
                                        throw new Error("须与目标一致");
                                    }
                                },
                            },
                        ]}
                    >
                        <Input/>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
}
