"use client";

import {CloudServerOutlined} from "@ant-design/icons";
import {
    Alert,
    Button,
    Drawer,
    Form,
    Input,
    message,
    Popconfirm,
    Select,
    Space,
    Spin,
    Table,
    Tag,
    Tooltip,
    Typography,
} from "antd";
import type {ColumnsType} from "antd/es/table";
import Link from "next/link";
import {useSearchParams} from "next/navigation";
import React, {Suspense} from "react";

import {isAbortError} from "@/lib/api/isAbortError";
import {DEFAULT_REQUEST_TIMEOUT_MS} from "@/lib/api/request";
import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {ModelConfigForm} from "@/components/ModelConfigForm";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {PageShell} from "@/components/PageShell";
import {SectionCard} from "@/components/SectionCard";
import {normalizeModelConfig} from "@/lib/model-config";
import {providerDisplayName} from "@/lib/model-config-labels";
import {
    createModel,
    deleteModel,
    getModelDetail,
    listModelProviders,
    listModels,
    type ModelFetchOpts,
    updateModel,
} from "@/lib/models/api";
import type {ModelFormValues, ModelSummary, ProviderMeta} from "@/lib/models/types";
import {tablePaginationFriendly} from "@/lib/table-pagination";
import {DRAWER_WIDTH_SIMPLE} from "@/lib/ui/sizes";

/** 与后端 ModelProvider 生成参数白名单保持一致（供离线 fallback） */
const FALLBACK_PROVIDERS: ProviderMeta[] = [
    {
        provider: "DASHSCOPE",
        chatProvider: true,
        modelKind: "CHAT",
        supportedConfigKeys: [
            "temperature",
            "topP",
            "topK",
            "maxTokens",
            "seed",
            "frequencyPenalty",
            "presencePenalty",
            "thinkingBudget",
            "reasoningEffort",
            "stream",
            "executionConfig",
            "toolChoice",
            "additionalHeaders",
            "additionalBodyParams",
            "additionalQueryParams",
        ],
    },
    {
        provider: "OPENAI",
        chatProvider: true,
        modelKind: "CHAT",
        supportedConfigKeys: [
            "temperature",
            "topP",
            "maxTokens",
            "maxCompletionTokens",
            "seed",
            "frequencyPenalty",
            "presencePenalty",
            "thinkingBudget",
            "reasoningEffort",
            "stream",
            "executionConfig",
            "toolChoice",
            "endpointPath",
            "additionalHeaders",
            "additionalBodyParams",
            "additionalQueryParams",
        ],
    },
    {
        provider: "ANTHROPIC",
        chatProvider: true,
        modelKind: "CHAT",
        supportedConfigKeys: [
            "temperature",
            "topP",
            "maxTokens",
            "maxCompletionTokens",
            "seed",
            "frequencyPenalty",
            "presencePenalty",
            "thinkingBudget",
            "reasoningEffort",
            "stream",
            "executionConfig",
            "toolChoice",
            "additionalHeaders",
            "additionalBodyParams",
            "additionalQueryParams",
        ],
    },
    {
        provider: "OPENAI_TEXT_EMBEDDING",
        chatProvider: false,
        modelKind: "EMBEDDING",
        supportedConfigKeys: ["dimensions", "executionConfig"],
    },
    {
        provider: "DASHSCOPE_TEXT_EMBEDDING",
        chatProvider: false,
        modelKind: "EMBEDDING",
        supportedConfigKeys: ["dimensions", "executionConfig"],
    },
];

function providerColor(provider: string): string {
    const p = provider.toUpperCase();
    if (p.includes("DASHSCOPE")) {
        return "purple";
    }
    if (p.includes("OPENAI")) {
        return "green";
    }
    if (p.includes("ANTHROPIC")) {
        return "orange";
    }
    return "default";
}

function ModelsPageContent() {
    const searchParams = useSearchParams();
    const editIdFromQuery = searchParams.get("edit");

    const [listLoading, setListLoading] = React.useState(false);
    const [rows, setRows] = React.useState<ModelSummary[]>([]);
    const [keyword, setKeyword] = React.useState("");
    const [providerFilter, setProviderFilter] = React.useState<string | undefined>(undefined);
    const [error, setError] = React.useState<unknown>(null);

    const [drawerOpen, setDrawerOpen] = React.useState(false);
    const [drawerMode, setDrawerMode] = React.useState<"create" | "edit">("create");
    const [editingId, setEditingId] = React.useState<string | null>(null);
    const [drawerLoading, setDrawerLoading] = React.useState(false);
    const [submitting, setSubmitting] = React.useState(false);
    const [form] = Form.useForm();

    const [providers, setProviders] = React.useState<ProviderMeta[]>(FALLBACK_PROVIDERS);
    const providerValue = Form.useWatch("provider", form);
    const activeProvider = providers.find((p) => p.provider === (providerValue ?? "").trim().toUpperCase());
    const isEmbeddingProvider = activeProvider?.chatProvider === false;

    const openedFromQueryRef = React.useRef<string | null>(null);

    async function loadProviders(opts?: ModelFetchOpts) {
        try {
            const data = await listModelProviders(opts);
            if (data.length > 0) {
                setProviders(data);
            }
        } catch {
            // 使用 fallback
        }
    }

    async function loadList(opts?: ModelFetchOpts) {
        setListLoading(true);
        setError(null);
        try {
            const data = await listModels(opts);
            setRows(data);
        } catch (e) {
            if (!isAbortError(e)) {
                setError(e);
            }
        } finally {
            setListLoading(false);
        }
    }

    React.useEffect(() => {
        const ac = new AbortController();
        const opts = {signal: ac.signal, timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS};
        void loadProviders(opts);
        void loadList(opts);
        return () => ac.abort();
    }, []);

    function openCreate() {
        setDrawerMode("create");
        setEditingId(null);
        form.resetFields();
        setDrawerOpen(true);
    }

    const openEdit = React.useCallback(async (id: string) => {
        setDrawerMode("edit");
        setEditingId(id);
        form.resetFields();
        setDrawerOpen(true);
        setDrawerLoading(true);
        setError(null);
        try {
            const detail = await getModelDetail(id, {timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS});
            form.setFieldsValue({
                name: detail.name ?? "",
                description: detail.description ?? "",
                provider: detail.provider,
                modelKey: detail.modelKey,
                baseUrl: detail.baseUrl ?? "",
                config: detail.config && Object.keys(detail.config).length > 0 ? {...detail.config} : {},
                apiKey: undefined,
            });
        } catch (e) {
            setError(e);
            setDrawerOpen(false);
        } finally {
            setDrawerLoading(false);
        }
    }, [form]);

    React.useEffect(() => {
        if (!editIdFromQuery || listLoading) {
            return;
        }
        if (openedFromQueryRef.current === editIdFromQuery) {
            return;
        }
        openedFromQueryRef.current = editIdFromQuery;
        const hit = rows.some((r) => r.id === editIdFromQuery);
        if (hit) {
            void openEdit(editIdFromQuery);
        } else {
            message.warning(`未找到 ID 为「${editIdFromQuery}」的模型，请从列表选择编辑`);
        }
    }, [editIdFromQuery, rows, listLoading, openEdit]);

    async function onDelete(id: string) {
        setError(null);
        try {
            await deleteModel(id, {timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS});
            void loadList({timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS});
            message.success("已删除");
        } catch (e) {
            setError(e);
        }
    }

    async function onSubmit(values: ModelFormValues) {
        setSubmitting(true);
        setError(null);
        try {
            const supportedKeys = activeProvider?.supportedConfigKeys ?? [];
            const normalizedConfig = normalizeModelConfig(values.config, supportedKeys);
            if (drawerMode === "create") {
                await createModel(
                    {
                        name: values.name.trim(),
                        description: values.description?.trim() ? values.description.trim() : undefined,
                        provider: values.provider,
                        modelKey: values.modelKey,
                        apiKey: values.apiKey,
                        baseUrl: values.baseUrl?.trim() ? values.baseUrl.trim() : undefined,
                        ...(normalizedConfig ? {config: normalizedConfig} : {}),
                    },
                    {timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS},
                );
                message.success("创建成功");
            } else if (editingId) {
                const body: Record<string, unknown> = {
                    name: values.name.trim(),
                    description: (values.description ?? "").trim(),
                    modelKey: values.modelKey.trim(),
                    baseUrl: values.baseUrl?.trim() ?? "",
                    config: normalizedConfig ?? {},
                };
                if (values.apiKey?.trim()) {
                    body.apiKey = values.apiKey.trim();
                }
                await updateModel(editingId, body, {timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS});
                message.success("已保存");
            }
            setDrawerOpen(false);
            await loadList({timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS});
        } catch (e) {
            setError(e);
        } finally {
            setSubmitting(false);
        }
    }

    const filtered = React.useMemo(() => {
        const k = keyword.trim().toLowerCase();
        return rows.filter((r) => {
            if (providerFilter && r.provider !== providerFilter) {
                return false;
            }
            if (!k) {
                return true;
            }
            return (
                r.id.toLowerCase().includes(k) ||
                (r.name ?? "").toLowerCase().includes(k) ||
                (r.description ?? "").toLowerCase().includes(k) ||
                (r.configSummary ?? "").toLowerCase().includes(k) ||
                r.provider.toLowerCase().includes(k) ||
                r.modelKey.toLowerCase().includes(k) ||
                (r.baseUrl ?? "").toLowerCase().includes(k)
            );
        });
    }, [rows, keyword, providerFilter]);

    const columns: ColumnsType<ModelSummary> = [
        {
            title: "配置名称",
            dataIndex: "name",
            width: 200,
            ellipsis: true,
            render: (v: string, record) => (
                <Tooltip title={v}>
                    <Space orientation="vertical" size={0}>
                        <Typography.Text strong ellipsis style={{maxWidth: 180}}>
                            {v}
                        </Typography.Text>
                        <Typography.Text type="secondary" ellipsis style={{maxWidth: 180, fontSize: 12}}>
                            {record.configSummary || "无参数摘要"}
                        </Typography.Text>
                    </Space>
                </Tooltip>
            ),
        },
        {
            title: "备注",
            dataIndex: "description",
            width: 140,
            ellipsis: true,
            render: (t: string | undefined) => t || "—",
        },
        {
            title: "提供方",
            dataIndex: "provider",
            width: 200,
            render: (v: string) => (
                <Space orientation="vertical" size={0}>
                    <Tag color={providerColor(v)}>{providerDisplayName(v)}</Tag>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        {v}
                    </Typography.Text>
                </Space>
            ),
        },
        {
            title: "模型标识",
            dataIndex: "modelKey",
            ellipsis: true,
        },
        {
            title: "接口根地址",
            dataIndex: "baseUrl",
            ellipsis: true,
            render: (v: string | undefined) =>
                v ? (
                    <Tooltip title={v}>
                        <Typography.Text ellipsis style={{maxWidth: 280}}>
                            {v}
                        </Typography.Text>
                    </Tooltip>
                ) : (
                    <Typography.Text type="secondary">-</Typography.Text>
                ),
        },
        {
            title: "创建时间",
            dataIndex: "createdAt",
            width: 200,
            render: (v: string | undefined) => v ?? "-",
        },
        {
            title: "操作",
            key: "actions",
            width: 220,
            fixed: "right",
            render: (_, record) => (
                <Space size="small" wrap>
                    <Link href={`/models/${record.id}`}>详情</Link>
                    <Button type="link" size="small" style={{padding: 0}} onClick={() => void openEdit(record.id)}>
                        编辑
                    </Button>
                    <Popconfirm
                        title="确认删除该模型？"
                        description="若智能体仍绑定此模型，删除会被拒绝。"
                        okText="删除"
                        cancelText="取消"
                        okButtonProps={{danger: true}}
                        onConfirm={() => void onDelete(record.id)}
                    >
                        <Button type="link" size="small" danger style={{padding: 0}}>
                            删除
                        </Button>
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    return (
        <AppLayout>
            <PageShell>
                <PageHeaderBlock
                    icon={<CloudServerOutlined/>}
                    title="模型管理"
                    subtitle="同一提供方、同一模型标识可保存多套「配置实例」。聊天模型 config 支持常见生成参数（采样、流式、惩罚、toolChoice、executionConfig 等）；Embedding 类型用于文本向量化等能力。"
                    extra={
                        <Space wrap>
                            <Button onClick={() => void loadList({timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS})}
                                    loading={listLoading}>
                                刷新
                            </Button>
                            <Button type="primary" onClick={openCreate}>
                                新建模型
                            </Button>
                        </Space>
                    }
                />

                <ErrorAlert error={error}/>

                <SectionCard title="模型列表">
                    <Space orientation="vertical" size={12} style={{width: "100%"}}>
                        <Space wrap style={{width: "100%", justifyContent: "space-between"}}>
                            <Space wrap>
                                <Input.Search
                                    allowClear
                                    placeholder="搜索编号、提供方、模型标识、接口地址…"
                                    style={{width: 320}}
                                    onSearch={setKeyword}
                                    onChange={(e) => setKeyword(e.target.value)}
                                />
                                <Select
                                    allowClear
                                    placeholder="按提供方筛选"
                                    style={{width: 280}}
                                    value={providerFilter}
                                    onChange={(v) => setProviderFilter(v)}
                                    options={providers.map((p) => ({
                                        value: p.provider,
                                        label: `${providerDisplayName(p.provider)}（${p.provider}） · ${
                                            p.modelKind ??
                                            (p.chatProvider === false ? "EMBEDDING" : "CHAT")
                                        }`,
                                    }))}
                                />
                            </Space>
                            <Typography.Text type="secondary">共 {filtered.length} 条</Typography.Text>
                        </Space>
                        <Table<ModelSummary>
                            rowKey="id"
                            loading={listLoading}
                            columns={columns}
                            dataSource={filtered}
                            scroll={{x: 1180}}
                            pagination={tablePaginationFriendly()}
                        />
                    </Space>
                </SectionCard>

                <Drawer
                    title={drawerMode === "create" ? "新建模型" : "编辑模型"}
                    size={DRAWER_WIDTH_SIMPLE}
                    open={drawerOpen}
                    onClose={() => setDrawerOpen(false)}
                    destroyOnHidden
                    extra={
                        <Space>
                            <Button onClick={() => setDrawerOpen(false)}>取消</Button>
                            <Button type="primary" loading={submitting} onClick={() => void form.submit()}>
                                保存
                            </Button>
                        </Space>
                    }
                >
                    <Spin spinning={drawerLoading}>
                        <Form
                            form={form}
                            layout="vertical"
                            initialValues={{config: {}, name: ""}}
                            onFinish={(v) => void onSubmit(v as ModelFormValues)}
                        >
                            <Form.Item
                                name="name"
                                label="配置名称（必填）"
                                rules={[{required: true, message: "请填写配置名称，便于与同名模型其他配置区分"}]}
                                tooltip="例如：通义-生产-低温度、GPT4-测试网关。同一 modelKey 可建多条。"
                            >
                                <Input placeholder="人类可读名称，用于列表与智能体下拉"/>
                            </Form.Item>
                            <Form.Item name="description" label="备注（可选）">
                                <Input.TextArea rows={2} placeholder="用途、环境、负责人等说明"/>
                            </Form.Item>
                            {drawerMode === "create" ? (
                                <Form.Item
                                    name="provider"
                                    label="提供方（provider）"
                                    rules={[{required: true, message: "请选择提供方"}]}
                                >
                                    <Select
                                        showSearch
                                        placeholder="请选择提供方"
                                        options={providers.map((p) => ({
                                            value: p.provider,
                                            label: `${providerDisplayName(p.provider)}（${p.provider}） · ${
                                                p.modelKind ??
                                                (p.chatProvider === false ? "EMBEDDING" : "CHAT")
                                            }`,
                                        }))}
                                        filterOption={(input, option) =>
                                            (option?.value ?? "").toString().toLowerCase().includes(input.toLowerCase())
                                        }
                                    />
                                </Form.Item>
                            ) : (
                                <>
                                    <Form.Item name="provider" hidden>
                                        <Input/>
                                    </Form.Item>
                                    <Form.Item label="提供方（不可修改）">
                                        <Typography.Text>
                                            <Tag color={providerColor((providerValue ?? "").toString())}>
                                                {providerDisplayName((providerValue ?? "-").toString())}
                                            </Tag>
                                            <Typography.Text type="secondary" style={{marginLeft: 8, fontSize: 12}}>
                                                {providerValue ?? "-"}
                                            </Typography.Text>
                                        </Typography.Text>
                                    </Form.Item>
                                </>
                            )}
                            <Form.Item
                                name="modelKey"
                                label="模型标识（modelKey）"
                                rules={[{required: true, message: "请输入模型标识"}]}
                                tooltip={
                                    isEmbeddingProvider
                                        ? "嵌入模型名，如 text-embedding-3-small、text-embedding-v4；与聊天 modelKey 不同。"
                                        : "厂商 API 中的模型名，如 qwen-plus、gpt-4o。"
                                }
                            >
                                <Input
                                    placeholder={
                                        isEmbeddingProvider
                                            ? "例如 text-embedding-3-small、text-embedding-v4"
                                            : "例如 qwen-plus、gpt-4o"
                                    }
                                />
                            </Form.Item>
                            <Form.Item
                                name="apiKey"
                                label={
                                    drawerMode === "create"
                                        ? "访问密钥（apiKey，可选）"
                                        : "访问密钥（apiKey，可选，留空不修改）"
                                }
                            >
                                <Input.Password
                                    placeholder={
                                        drawerMode === "create"
                                            ? "仅用于本地联调"
                                            : "仅在需要轮换密钥时填写；留空表示保持原密钥"
                                    }
                                />
                            </Form.Item>
                            <Form.Item name="baseUrl" label="接口根地址（baseUrl，可选，可清空）">
                                <Input
                                    placeholder={
                                        isEmbeddingProvider
                                            ? "嵌入网关根地址，需能访问 Embeddings 路径"
                                            : "私有化网关或兼容 OpenAI 的接口根地址"
                                    }
                                />
                            </Form.Item>
                            {activeProvider && isEmbeddingProvider ? (
                                <Alert
                                    type="info"
                                    showIcon
                                    style={{marginBottom: 12}}
                                    title="当前为文本嵌入（Embedding）配置"
                                    description="不会出现 temperature、maxTokens、toolChoice 等聊天采样参数；Embedding 类型仅支持 dimensions、executionConfig（超时/重试）及 baseUrl/apiKey。用于知识库向量化等。"
                                />
                            ) : null}
                            <Form.Item
                                name="config"
                                label={
                                    isEmbeddingProvider
                                        ? "嵌入调用参数（可选）"
                                        : "默认推理与请求参数（可选）"
                                }
                                tooltip={
                                    isEmbeddingProvider
                                        ? "嵌入模型配置：向量维度、编码、路径及附加头/正文/查询参数。"
                                        : "根据提供方展示采样、输出长度等；扩展项可通过「请求头 / 正文 / 查询参数」填写。"
                                }
                            >
                                {activeProvider ? (
                                    <ModelConfigForm
                                        supportedKeys={activeProvider.supportedConfigKeys}
                                        chatProvider={activeProvider.chatProvider !== false}
                                    />
                                ) : (
                                    <Typography.Text type="secondary">请先选择提供方。</Typography.Text>
                                )}
                            </Form.Item>
                        </Form>
                    </Spin>
                </Drawer>
            </PageShell>
        </AppLayout>
    );
}

export default function ModelsPage() {
    return (
        <Suspense
            fallback={
                <AppLayout>
                    <Spin style={{display: "block", margin: "48px auto"}}/>
                </AppLayout>
            }
        >
            <ModelsPageContent/>
        </Suspense>
    );
}
