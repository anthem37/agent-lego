"use client";

import {
    ClusterOutlined,
    PlusOutlined,
    QuestionCircleOutlined,
    SearchOutlined,
    ThunderboltOutlined,
} from "@ant-design/icons";
import {
    Alert,
    Button,
    Checkbox,
    Col,
    Divider,
    Empty,
    Form,
    Input,
    InputNumber,
    message,
    Modal,
    Popover,
    Row,
    Select,
    Space,
    Spin,
    Tag,
    Tooltip,
    Typography,
} from "antd";
import Link from "next/link";
import {useRouter, useSearchParams} from "next/navigation";
import React, {Suspense} from "react";

import kbShell from "@/components/kb/kb-shell.module.css";
import {AppLayout} from "@/components/AppLayout";
import {VectorStoreOpsPanel} from "@/components/vector-store/VectorStoreOpsPanel";
import {ErrorAlert} from "@/components/ErrorAlert";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {PageShell} from "@/components/PageShell";
import {listModelsAsSelectRows} from "@/lib/models/api";
import {modelNameForId, type ModelOptionRow, toModelSelectOptions} from "@/lib/model-select-options";
import {buildVectorStoreConnectionConfig} from "@/lib/vector-store/build-connection-config";
import {
    createVectorStoreProfile,
    deleteVectorStoreProfile,
    listVectorStoreProfiles,
    probeVectorStoreProfile
} from "@/lib/vector-store/api";
import type {VectorStoreProfileDto} from "@/lib/vector-store/types";

type CreateForm = {
    name: string;
    vectorStoreKind: "MILVUS" | "QDRANT";
    embeddingModelId: string;
    milvusHost: string;
    milvusPort: number;
    milvusCollectionName: string;
    milvusToken?: string;
    milvusSecure?: boolean;
    qdrantHost: string;
    qdrantPort: number;
    qdrantCollectionName: string;
    qdrantApiKey?: string;
    qdrantSecure?: boolean;
    qdrantDistance?: string;
};

function VectorStorePageBody() {
    const router = useRouter();
    const searchParams = useSearchParams();
    const [rows, setRows] = React.useState<VectorStoreProfileDto[]>([]);
    const [loading, setLoading] = React.useState(true);
    const [error, setError] = React.useState<unknown>(null);
    const [modelRows, setModelRows] = React.useState<ModelOptionRow[]>([]);
    const [modalOpen, setModalOpen] = React.useState(false);
    const [submitting, setSubmitting] = React.useState(false);
    const [form] = Form.useForm<CreateForm>();
    const [selectedProfileId, setSelectedProfileId] = React.useState<string | undefined>(undefined);

    const vectorStoreKind = Form.useWatch("vectorStoreKind", form) ?? "MILVUS";

    const embeddingRows = React.useMemo(
        () => modelRows.filter((m) => m.chatProvider === false),
        [modelRows],
    );

    const [quickProbeId, setQuickProbeId] = React.useState<string | null>(null);
    const [profileQuery, setProfileQuery] = React.useState("");

    const pushProfile = React.useCallback(
        (id: string | undefined) => {
            setSelectedProfileId(id);
            if (id) {
                router.replace(`/vector-store?profile=${encodeURIComponent(id)}`, {scroll: false});
            } else {
                router.replace("/vector-store", {scroll: false});
            }
        },
        [router],
    );

    React.useEffect(() => {
        const p = searchParams.get("profile");
        setSelectedProfileId(p ?? undefined);
    }, [searchParams]);

    const filteredRows = React.useMemo(() => {
        const q = profileQuery.trim().toLowerCase();
        if (!q) {
            return rows;
        }
        return rows.filter((r) => {
            const embName = modelNameForId(embeddingRows, r.embeddingModelId).toLowerCase();
            return (
                r.name.toLowerCase().includes(q) ||
                r.id.toLowerCase().includes(q) ||
                (r.vectorStoreKind ?? "").toLowerCase().includes(q) ||
                r.embeddingModelId.toLowerCase().includes(q) ||
                embName.includes(q) ||
                String(r.embeddingDims ?? "").includes(q)
            );
        });
    }, [rows, profileQuery, embeddingRows]);

    async function refresh() {
        setLoading(true);
        setError(null);
        try {
            const list = await listVectorStoreProfiles();
            setRows(list);
        } catch (e) {
            setError(e);
            setRows([]);
        } finally {
            setLoading(false);
        }
    }

    React.useEffect(() => {
        let cancelled = false;
        void (async () => {
            try {
                const models = await listModelsAsSelectRows();
                if (!cancelled) {
                    setModelRows(models);
                }
            } catch {
                if (!cancelled) {
                    setModelRows([]);
                }
            }
        })();
        return () => {
            cancelled = true;
        };
    }, []);

    React.useEffect(() => {
        void refresh();
    }, []);

    function resetCreateFormDefaults() {
        form.setFieldsValue({
            name: "",
            vectorStoreKind: "MILVUS",
            embeddingModelId: undefined,
            milvusHost: "localhost",
            milvusPort: 19530,
            milvusCollectionName: "",
            milvusToken: undefined,
            milvusSecure: false,
            qdrantHost: "localhost",
            qdrantPort: 6333,
            qdrantCollectionName: "",
            qdrantApiKey: undefined,
            qdrantSecure: false,
            qdrantDistance: undefined,
        });
    }

    async function onCreate(values: CreateForm) {
        setSubmitting(true);
        try {
            const kind = values.vectorStoreKind;
            const vectorStoreConfig = buildVectorStoreConnectionConfig(kind, values);
            const created = await createVectorStoreProfile({
                name: values.name.trim(),
                vectorStoreKind: kind,
                embeddingModelId: values.embeddingModelId,
                vectorStoreConfig,
            });
            message.success("已创建向量库配置");
            setModalOpen(false);
            await refresh();
            pushProfile(created.id);
        } catch (e) {
            setError(e);
        } finally {
            setSubmitting(false);
        }
    }

    function onDelete(id: string) {
        Modal.confirm({
            title: "确定删除这条连接？",
            content:
                "删除后，引用它的知识库 / 智能体记忆策略会失效。若正在使用，请先到「知识库」「智能体」里改掉引用，再回来删。",
            okType: "danger",
            onOk: async () => {
                try {
                    await deleteVectorStoreProfile(id);
                    message.success("已删除");
                    if (selectedProfileId === id) {
                        setSelectedProfileId(undefined);
                        router.replace("/vector-store", {scroll: false});
                    }
                    await refresh();
                } catch (e) {
                    setError(e);
                }
            },
        });
    }

    async function runQuickProbe(pid: string) {
        setQuickProbeId(pid);
        try {
            const r = await probeVectorStoreProfile(pid);
            if (r.ok) {
                message.success(`探测成功，耗时 ${r.latencyMs} ms`);
            } else {
                message.warning(r.message ?? "探测失败");
            }
        } catch (e) {
            setError(e);
        } finally {
            setQuickProbeId(null);
        }
    }

    return (
        <AppLayout>
            <PageShell gap={20}>
                <div className={kbShell.pageIntro}>
                    <PageHeaderBlock
                        icon={<ClusterOutlined/>}
                        title="向量库"
                        subtitle={
                            <>
                                保存 Milvus/Qdrant 连接与嵌入模型；左侧<strong>点选连接</strong>，右侧查看引用、远程
                                collection 与数据预览（布局与知识库一致）。
                                <Typography.Paragraph style={{margin: "8px 0 0", fontSize: 13}}>
                                    <Link href="/models">模型</Link>
                                    {" · "}
                                    <Link href="/kb">知识库</Link>
                                </Typography.Paragraph>
                            </>
                        }
                        extra={
                            <Popover
                                title="说明"
                                content={
                                    <ul style={{margin: 0, paddingLeft: 18, maxWidth: 300}}>
                                        <li>本页：保存连接 + 嵌入模型配置。</li>
                                        <li>左侧选中后，右侧展示运维与引用。</li>
                                        <li>
                                            需先准备 <Link href="/models">Embedding 模型</Link>。
                                        </li>
                                    </ul>
                                }
                            >
                                <Button type="text" size="small" icon={<QuestionCircleOutlined/>}>
                                    说明
                                </Button>
                            </Popover>
                        }
                    />
                </div>

                <ErrorAlert error={error}/>

                <Row gutter={[20, 20]}>
                    <Col xs={24} lg={6}>
                        <div className={kbShell.shellPanel}>
                            <div className={kbShell.shellHeader}>
                                <div style={{minWidth: 0, flex: 1}}>
                                    <div className={kbShell.shellHeaderTitle}>连接配置</div>
                                    <p className={kbShell.shellHeaderHint}>点选后在右侧查看引用与远程运维</p>
                                </div>
                                <Button type="primary" size="small" icon={<PlusOutlined/>}
                                        onClick={() => setModalOpen(true)}>
                                    新建
                                </Button>
                            </div>
                            <div className={kbShell.shellSearch}>
                                <Input
                                    allowClear
                                    size="middle"
                                    prefix={<SearchOutlined style={{color: "rgba(0,0,0,0.35)"}}/>}
                                    placeholder="搜索名称、类型、嵌入模型…"
                                    value={profileQuery}
                                    onChange={(e) => setProfileQuery(e.target.value)}
                                />
                            </div>
                            <div style={{padding: "0 16px 12px"}}>
                                <Space wrap>
                                    <Tooltip title="从服务器重新拉一遍列表">
                                        <Button size="small" onClick={() => void refresh()} loading={loading}>
                                            刷新列表
                                        </Button>
                                    </Tooltip>
                                </Space>
                            </div>
                            <div className={kbShell.shellListScroll}>
                                {loading ? (
                                    <div style={{padding: 32, textAlign: "center"}}>
                                        <Spin/>
                                    </div>
                                ) : filteredRows.length === 0 ? (
                                    <Empty
                                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                                        description={
                                            <Typography.Paragraph style={{marginBottom: 0}}>
                                                还没有保存任何连接。
                                                <br/>
                                                请先新建，填写 Milvus/Qdrant 地址与嵌入模型。
                                            </Typography.Paragraph>
                                        }
                                    >
                                        <Button type="primary" size="small" icon={<PlusOutlined/>}
                                                onClick={() => setModalOpen(true)}>
                                            新建第一条连接
                                        </Button>
                                    </Empty>
                                ) : (
                                    filteredRows.map((item) => {
                                        const active = item.id === selectedProfileId;
                                        const embName = modelNameForId(embeddingRows, item.embeddingModelId);
                                        return (
                                            <div
                                                key={item.id}
                                                className={`${kbShell.collectionItem} ${active ? kbShell.collectionItemActive : ""}`}
                                                onClick={() => pushProfile(item.id)}
                                                role="presentation"
                                                style={{
                                                    display: "flex",
                                                    alignItems: "flex-start",
                                                    justifyContent: "space-between",
                                                    gap: 12,
                                                }}
                                            >
                                                <div style={{minWidth: 0, flex: 1}}>
                                                    <Space wrap size={4} style={{marginBottom: 4}}>
                                                        <Typography.Text strong ellipsis style={{maxWidth: 200}}>
                                                            {item.name}
                                                        </Typography.Text>
                                                        <Tag color="blue">{item.vectorStoreKind}</Tag>
                                                    </Space>
                                                    <Space orientation="vertical" size={2} style={{width: "100%"}}>
                                                        <Typography.Text type="secondary" style={{fontSize: 12}}
                                                                         ellipsis>
                                                            嵌入{" "}
                                                            <Link
                                                                href={`/models/${encodeURIComponent(item.embeddingModelId)}`}
                                                                onClick={(e) => e.stopPropagation()}
                                                            >
                                                                {embName}
                                                            </Link>
                                                            {" · "}
                                                            {item.embeddingDims ?? "—"} 维
                                                        </Typography.Text>
                                                        <Typography.Text type="secondary" copyable={{text: item.id}}
                                                                         style={{fontSize: 11}}>
                                                            {item.id.length > 20 ? `${item.id.slice(0, 18)}…` : item.id}
                                                        </Typography.Text>
                                                    </Space>
                                                </div>
                                                <div onClick={(e) => e.stopPropagation()} role="presentation">
                                                    <Space size={4}>
                                                        <Tooltip title="探测连通性（不修改数据）">
                                                            <Button
                                                                type="text"
                                                                size="small"
                                                                icon={<ThunderboltOutlined/>}
                                                                loading={quickProbeId === item.id}
                                                                onClick={(e) => {
                                                                    e.stopPropagation();
                                                                    void runQuickProbe(item.id);
                                                                }}
                                                            />
                                                        </Tooltip>
                                                        <Tooltip title="删除此连接配置">
                                                            <Button
                                                                type="text"
                                                                danger
                                                                size="small"
                                                                onClick={(e) => {
                                                                    e.stopPropagation();
                                                                    void onDelete(item.id);
                                                                }}
                                                            >
                                                                删除
                                                            </Button>
                                                        </Tooltip>
                                                    </Space>
                                                </div>
                                            </div>
                                        );
                                    })
                                )}
                            </div>
                        </div>
                    </Col>
                    <Col xs={24} lg={18}>
                        <div className={kbShell.mainPanel}>
                            {selectedProfileId ? (
                                <div className={kbShell.mainBody} style={{padding: 16}}>
                                    <VectorStoreOpsPanel
                                        key={selectedProfileId}
                                        variant="inline"
                                        controlledProfileId={selectedProfileId}
                                        onClearSelection={() => pushProfile(undefined)}
                                        externalProfiles={rows}
                                        profilesLoading={loading}
                                        onRefreshProfiles={() => refresh()}
                                        embeddingModelRows={embeddingRows}
                                    />
                                </div>
                            ) : (
                                <div className={kbShell.emptyState}>
                                    <Empty
                                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                                        description={
                                            <Space orientation="vertical" size={8}>
                                                <Typography.Text>未选择连接</Typography.Text>
                                                <Typography.Text type="secondary">
                                                    在左侧列表中点击一条连接，即可查看引用、远程 collection 列表与数据预览等。
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
                    title="新建向量库连接"
                    open={modalOpen}
                    onCancel={() => setModalOpen(false)}
                    footer={null}
                    destroyOnHidden
                    width={640}
                    afterOpenChange={(open) => {
                        if (open) {
                            resetCreateFormDefaults();
                        }
                    }}
                >
                    <Alert
                        type="info"
                        showIcon
                        style={{marginBottom: 16}}
                        title="你在填什么？"
                        description="下面填的是「向量数据库在哪里」+「算向量用哪个模型」。保存后不会自动建表；建知识库或写记忆时再指定表名即可。"
                    />
                    {embeddingRows.length === 0 ? (
                        <Alert
                            type="warning"
                            showIcon
                            style={{marginBottom: 16}}
                            title="还不能保存：缺少嵌入模型"
                            description={
                                <span>
                                    请先去 <Link href="/models">模型</Link> 页面，新建一个类型为{" "}
                                    <strong>Embedding</strong> 的模型，再回来填这个表单。
                                </span>
                            }
                        />
                    ) : null}
                    <Form<CreateForm>
                        form={form}
                        layout="vertical"
                        onFinish={onCreate}
                        initialValues={{
                            vectorStoreKind: "MILVUS",
                            milvusHost: "localhost",
                            milvusPort: 19530,
                            milvusCollectionName: "",
                            milvusSecure: false,
                            qdrantHost: "localhost",
                            qdrantPort: 6333,
                            qdrantCollectionName: "",
                            qdrantSecure: false,
                        }}
                    >
                        <Form.Item name="name" label="配置名称" rules={[{required: true, message: "请输入名称"}]}>
                            <Input placeholder="例如：生产 Milvus 主库"/>
                        </Form.Item>
                        <Form.Item
                            name="vectorStoreKind"
                            label="向量库类型"
                            rules={[{required: true, message: "请选择类型"}]}
                        >
                            <Select
                                options={[
                                    {label: "Milvus", value: "MILVUS"},
                                    {label: "Qdrant（REST）", value: "QDRANT"},
                                ]}
                            />
                        </Form.Item>

                        <Divider plain style={{margin: "12px 0"}}>
                            连接信息
                        </Divider>
                        <Typography.Paragraph type="secondary" style={{marginTop: -4, marginBottom: 12}}>
                            仅填写连接信息即可；物理 collection 名称可在知识库创建或记忆写入时单独指定。
                        </Typography.Paragraph>

                        {vectorStoreKind === "QDRANT" ? (
                            <>
                                <Form.Item
                                    name="qdrantHost"
                                    label="Qdrant 主机"
                                    rules={[{required: true, message: "请输入主机名或 IP"}]}
                                >
                                    <Input placeholder="例如 localhost"/>
                                </Form.Item>
                                <Form.Item
                                    name="qdrantPort"
                                    label="Qdrant HTTP 端口"
                                    rules={[
                                        {required: true, message: "请输入端口"},
                                        {
                                            type: "number",
                                            min: 1,
                                            max: 65535,
                                            message: "端口范围 1～65535",
                                        },
                                    ]}
                                >
                                    <InputNumber min={1} max={65535} style={{width: "100%"}}/>
                                </Form.Item>
                                <Form.Item
                                    name="qdrantCollectionName"
                                    label="默认物理 collection（可选）"
                                    rules={[
                                        {
                                            pattern: /^$|^[a-zA-Z0-9_-]+$/,
                                            message: "仅允许字母、数字、下划线与连字符",
                                        },
                                    ]}
                                    extra="留空则不在 profile 中保存 collectionName；知识库创建时可填覆盖名。"
                                >
                                    <Input placeholder="可选，例如 shared_kb_v1"/>
                                </Form.Item>
                                <Form.Item name="qdrantApiKey" label="API Key（可选）">
                                    <Input.Password placeholder="Qdrant Cloud 等"/>
                                </Form.Item>
                                <Form.Item name="qdrantSecure" valuePropName="checked">
                                    <Checkbox>使用 HTTPS（secure）</Checkbox>
                                </Form.Item>
                                <Form.Item
                                    name="qdrantDistance"
                                    label="距离度量（可选）"
                                    extra="默认 COSINE；可选 DOT、EUCLID。"
                                >
                                    <Select
                                        allowClear
                                        placeholder="默认 COSINE"
                                        options={[
                                            {label: "COSINE", value: "COSINE"},
                                            {label: "DOT", value: "DOT"},
                                            {label: "EUCLID", value: "EUCLID"},
                                        ]}
                                    />
                                </Form.Item>
                            </>
                        ) : (
                            <>
                                <Form.Item
                                    name="milvusHost"
                                    label="Milvus 主机"
                                    rules={[{required: true, message: "请输入主机名或 IP"}]}
                                >
                                    <Input placeholder="例如 localhost 或 milvus.example.com"/>
                                </Form.Item>
                                <Form.Item
                                    name="milvusPort"
                                    label="Milvus 端口"
                                    rules={[
                                        {required: true, message: "请输入端口"},
                                        {type: "number", min: 1, max: 65535, message: "端口范围 1～65535"},
                                    ]}
                                >
                                    <InputNumber min={1} max={65535} style={{width: "100%"}}/>
                                </Form.Item>
                                <Form.Item
                                    name="milvusCollectionName"
                                    label="默认物理 collection（可选）"
                                    rules={[
                                        {
                                            pattern: /^$|^[a-zA-Z0-9_]+$/,
                                            message: "仅允许字母、数字与下划线",
                                        },
                                    ]}
                                    extra="留空则不在 profile 中保存 collectionName；知识库创建时可填覆盖名。"
                                >
                                    <Input placeholder="可选，例如 shared_kb_v1"/>
                                </Form.Item>
                                <Form.Item name="milvusToken" label="Token（可选）">
                                    <Input.Password placeholder="Zilliz Cloud 等场景"/>
                                </Form.Item>
                                <Form.Item name="milvusSecure" valuePropName="checked">
                                    <Checkbox>使用 TLS（secure）</Checkbox>
                                </Form.Item>
                            </>
                        )}

                        <Form.Item
                            name="embeddingModelId"
                            label="嵌入模型"
                            rules={[{required: true, message: "请选择嵌入模型"}]}
                            extra="维度由模型配置解析；须与记忆/知识库写入时选用的一致。"
                        >
                            <Select
                                showSearch
                                placeholder="选择 Embedding 模型配置"
                                options={toModelSelectOptions(embeddingRows)}
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
                                <Button onClick={() => setModalOpen(false)}>取消</Button>
                                <Button type="primary" htmlType="submit" loading={submitting}>
                                    创建
                                </Button>
                            </Space>
                        </Form.Item>
                    </Form>
                </Modal>
            </PageShell>
        </AppLayout>
    );
}

export default function VectorStorePage() {
    return (
        <Suspense
            fallback={
                <AppLayout>
                    <PageShell>
                        <div style={{padding: 48, textAlign: "center"}}>
                            <Spin size="large"/>
                        </div>
                    </PageShell>
                </AppLayout>
            }
        >
            <VectorStorePageBody/>
        </Suspense>
    );
}
