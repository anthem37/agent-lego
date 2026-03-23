"use client";

import {DatabaseOutlined, DeleteOutlined, EditOutlined, PlusOutlined} from "@ant-design/icons";
import {
    Alert,
    Button,
    Card,
    Checkbox,
    Form,
    Input,
    InputNumber,
    message,
    Modal,
    Select,
    Space,
    Table,
    Tag,
    Tooltip,
    Typography,
} from "antd";
import Link from "next/link";
import React from "react";

import {isAbortError} from "@/lib/api/isAbortError";
import {DEFAULT_REQUEST_TIMEOUT_MS} from "@/lib/api/request";
import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {PageShell} from "@/components/PageShell";
import {SectionCard} from "@/components/SectionCard";
import {
    createMemoryPolicy,
    deleteMemoryPolicy,
    listMemoryPolicies,
    type MemoryPolicyDto,
    updateMemoryPolicy,
} from "@/lib/memory-policies/api";
import {listVectorStoreProfiles} from "@/lib/vector-store/api";
import type {VectorStoreProfileDto} from "@/lib/vector-store/types";
import {
    RETRIEVAL_OPTIONS,
    retrievalLabel,
    SCOPE_KIND_OPTIONS,
    STRATEGY_OPTIONS,
    strategyLabel,
    WRITE_MODE_OPTIONS,
    writeModeLabel,
} from "@/lib/memory-policies/semantics";

type PolicyForm = {
    name: string;
    description?: string;
    ownerScope: string;
    strategyKind?: string;
    scopeKind?: string;
    retrievalMode?: string;
    topK?: number;
    writeMode?: string;
    writeBackOnDuplicate?: "skip" | "upsert";
    /** ASSISTANT_SUMMARY 粗略摘要字符上限；空表示未配置（默认 480） */
    roughSummaryMaxChars?: number | null;
    /** 仅编辑：勾选后提交时清空库中上限 */
    clearRoughSummaryMaxChars?: boolean;
    vectorStoreProfileId?: string;
    vectorCollectionName?: string;
    vectorMinScore?: number;
    clearVectorLink?: boolean;
};

export default function MemoryPoliciesPage() {
    const [error, setError] = React.useState<unknown>(null);
    const [loading, setLoading] = React.useState(false);
    const [rows, setRows] = React.useState<MemoryPolicyDto[]>([]);
    const [modalOpen, setModalOpen] = React.useState(false);
    const [editing, setEditing] = React.useState<MemoryPolicyDto | null>(null);
    const [saving, setSaving] = React.useState(false);
    const [form] = Form.useForm<PolicyForm>();
    const watchedRetrieval = Form.useWatch("retrievalMode", form);
    const watchedWrite = Form.useWatch("writeMode", form);
    const watchedClearRough = Form.useWatch("clearRoughSummaryMaxChars", form);
    const watchedClearVector = Form.useWatch("clearVectorLink", form);

    const [implFilter, setImplFilter] = React.useState<"all" | "warn" | "none">("all");
    const [vectorProfiles, setVectorProfiles] = React.useState<VectorStoreProfileDto[]>([]);

    React.useEffect(() => {
        if (!modalOpen) {
            return;
        }
        const ac = new AbortController();
        void listVectorStoreProfiles({signal: ac.signal, timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS})
            .then((rows) => setVectorProfiles(Array.isArray(rows) ? rows : []))
            .catch(() => {
                if (!ac.signal.aborted) {
                    setVectorProfiles([]);
                }
            });
        return () => ac.abort();
    }, [modalOpen]);

    const displayedRows = React.useMemo(() => {
        if (implFilter === "all") {
            return rows;
        }
        return rows.filter((p) => {
            const n = p.implementationWarnings?.length ?? 0;
            if (implFilter === "warn") {
                return n > 0;
            }
            return n === 0;
        });
    }, [rows, implFilter]);

    const load = React.useCallback(async (signal?: AbortSignal) => {
        setError(null);
        setLoading(true);
        const fetchOpts = {signal, timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS};
        try {
            const data = await listMemoryPolicies(fetchOpts);
            setRows(Array.isArray(data) ? data : []);
        } catch (e) {
            if (!isAbortError(e)) {
                setError(e);
                setRows([]);
            }
        } finally {
            setLoading(false);
        }
    }, []);

    React.useEffect(() => {
        const ac = new AbortController();
        void load(ac.signal);
        return () => ac.abort();
    }, [load]);

    function openCreate() {
        setEditing(null);
        form.resetFields();
        form.setFieldsValue({
            strategyKind: "EPISODIC_DIALOGUE",
            scopeKind: "CUSTOM_NAMESPACE",
            retrievalMode: "KEYWORD",
            topK: 5,
            writeMode: "OFF",
            writeBackOnDuplicate: "skip",
            clearRoughSummaryMaxChars: false,
            clearVectorLink: false,
        });
        setModalOpen(true);
    }

    function openEdit(p: MemoryPolicyDto) {
        setEditing(p);
        form.setFieldsValue({
            name: p.name,
            description: p.description,
            ownerScope: p.ownerScope,
            strategyKind: p.strategyKind ?? "EPISODIC_DIALOGUE",
            scopeKind: p.scopeKind ?? "CUSTOM_NAMESPACE",
            retrievalMode: p.retrievalMode ?? "KEYWORD",
            topK: p.topK ?? 5,
            writeMode: p.writeMode ?? "OFF",
            writeBackOnDuplicate: (p.writeBackOnDuplicate === "upsert" ? "upsert" : "skip") as "skip" | "upsert",
            roughSummaryMaxChars:
                typeof p.roughSummaryMaxChars === "number" ? p.roughSummaryMaxChars : undefined,
            clearRoughSummaryMaxChars: false,
            vectorStoreProfileId: p.vectorStoreProfileId,
            vectorCollectionName:
                typeof p.vectorStoreConfig?.collectionName === "string"
                    ? p.vectorStoreConfig.collectionName
                    : undefined,
            vectorMinScore: typeof p.vectorMinScore === "number" ? p.vectorMinScore : undefined,
            clearVectorLink: false,
        });
        setModalOpen(true);
    }

    async function onSubmit(values: PolicyForm) {
        setSaving(true);
        setError(null);
        try {
            const body: Parameters<typeof updateMemoryPolicy>[1] = {
                name: values.name.trim(),
                description: values.description?.trim(),
                ownerScope: values.ownerScope.trim(),
                strategyKind: values.strategyKind,
                scopeKind: values.scopeKind,
                retrievalMode: values.retrievalMode,
                topK: values.topK,
                writeMode: values.writeMode,
                writeBackOnDuplicate: values.writeBackOnDuplicate ?? "skip",
            };
            const rm = values.retrievalMode ?? "KEYWORD";
            if (editing) {
                if (values.clearRoughSummaryMaxChars) {
                    body.clearRoughSummaryMaxChars = true;
                } else if (typeof values.roughSummaryMaxChars === "number") {
                    body.roughSummaryMaxChars = values.roughSummaryMaxChars;
                }
                if (values.clearVectorLink) {
                    body.clearVectorLink = true;
                } else {
                    if (values.vectorStoreProfileId) {
                        body.vectorStoreProfileId = values.vectorStoreProfileId;
                    }
                    if (values.vectorCollectionName?.trim()) {
                        body.vectorStoreConfig = {collectionName: values.vectorCollectionName.trim()};
                    }
                    if (typeof values.vectorMinScore === "number") {
                        body.vectorMinScore = values.vectorMinScore;
                    }
                }
                await updateMemoryPolicy(editing.id, body, {timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS});
                message.success("策略已更新");
            } else {
                if (typeof values.roughSummaryMaxChars === "number") {
                    body.roughSummaryMaxChars = values.roughSummaryMaxChars;
                }
                if (rm === "VECTOR" || rm === "HYBRID") {
                    if (values.vectorStoreProfileId) {
                        body.vectorStoreProfileId = values.vectorStoreProfileId;
                    }
                    if (values.vectorCollectionName?.trim()) {
                        body.vectorStoreConfig = {collectionName: values.vectorCollectionName.trim()};
                    }
                    if (typeof values.vectorMinScore === "number") {
                        body.vectorMinScore = values.vectorMinScore;
                    }
                }
                await createMemoryPolicy(body, {timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS});
                message.success("策略已创建");
            }
            setModalOpen(false);
            await load();
        } catch (e) {
            setError(e);
        } finally {
            setSaving(false);
        }
    }

    return (
        <AppLayout>
            <PageShell>
                <PageHeaderBlock
                    icon={<DatabaseOutlined/>}
                    title="记忆策略"
                    subtitle="定义「记什么、怎么取、怎么写」——与知识库 RAG 独立；领域说明见仓库 docs/memory-strategy.md。智能体绑定策略 ID 后生效。"
                />

                <ErrorAlert error={error}/>

                <SectionCard
                    title="策略列表"
                    extra={
                        <Space wrap>
                            <Select
                                value={implFilter}
                                onChange={(v) => setImplFilter(v)}
                                style={{width: 148}}
                                options={[
                                    {value: "all", label: "全部"},
                                    {value: "warn", label: "有实现提示"},
                                    {value: "none", label: "无提示"},
                                ]}
                            />
                            <Button type="primary" icon={<PlusOutlined/>} onClick={openCreate}>
                                新建策略
                            </Button>
                        </Space>
                    }
                >
                    <Table<MemoryPolicyDto>
                        size="small"
                        rowKey="id"
                        loading={loading}
                        dataSource={displayedRows}
                        pagination={false}
                        scroll={{x: true}}
                        columns={[
                            {
                                title: "名称",
                                dataIndex: "name",
                                width: 200,
                                ellipsis: true,
                            },
                            {
                                title: "owner_scope",
                                dataIndex: "ownerScope",
                                ellipsis: true,
                                render: (v: string) => <Typography.Text code>{v}</Typography.Text>,
                            },
                            {
                                title: "策略目的",
                                dataIndex: "strategyKind",
                                width: 140,
                                ellipsis: true,
                                render: (v: string) => strategyLabel(v),
                            },
                            {
                                title: "检索",
                                dataIndex: "retrievalMode",
                                width: 120,
                                render: (v: string) => retrievalLabel(v),
                            },
                            {
                                title: "写入",
                                dataIndex: "writeMode",
                                width: 120,
                                render: (v: string) => writeModeLabel(v),
                            },
                            {
                                title: "topK",
                                dataIndex: "topK",
                                width: 64,
                            },
                            {
                                title: "引用智能体",
                                dataIndex: "referencingAgentCount",
                                width: 100,
                                render: (n: number | undefined) =>
                                    typeof n === "number" ? (
                                        <Typography.Text strong={n > 0}>{n}</Typography.Text>
                                    ) : (
                                        "—"
                                    ),
                            },
                            {
                                title: "去重",
                                dataIndex: "writeBackOnDuplicate",
                                width: 72,
                            },
                            {
                                title: "实现提示",
                                width: 96,
                                render: (_, p) =>
                                    p.implementationWarnings && p.implementationWarnings.length > 0 ? (
                                        <Tooltip
                                            title={
                                                <ul style={{margin: 0, paddingLeft: 16, maxWidth: 360}}>
                                                    {p.implementationWarnings.map((w) => (
                                                        <li key={w}>{w}</li>
                                                    ))}
                                                </ul>
                                            }
                                        >
                                            <Tag color="warning">{p.implementationWarnings.length} 条</Tag>
                                        </Tooltip>
                                    ) : (
                                        <Typography.Text type="secondary">—</Typography.Text>
                                    ),
                            },
                            {
                                title: "操作",
                                width: 220,
                                render: (_, p) => (
                                    <Space wrap>
                                        <Link href={`/memory-policies/${p.id}`}>条目与详情</Link>
                                        <Button type="link" size="small" icon={<EditOutlined/>}
                                                onClick={() => openEdit(p)}>
                                            编辑
                                        </Button>
                                        <Button
                                            type="link"
                                            danger
                                            size="small"
                                            icon={<DeleteOutlined/>}
                                            onClick={() => {
                                                Modal.confirm({
                                                    title: "删除该记忆策略？",
                                                    content:
                                                        "将级联删除其下所有记忆条目。若仍有智能体绑定该策略，删除会被拒绝，请先在智能体详情中改绑。",
                                                    okButtonProps: {danger: true},
                                                    onOk: async () => {
                                                        try {
                                                            await deleteMemoryPolicy(p.id, {
                                                                timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS,
                                                            });
                                                            message.success("已删除");
                                                            await load();
                                                        } catch (e) {
                                                            setError(e);
                                                        }
                                                    },
                                                });
                                            }}
                                        >
                                            删除
                                        </Button>
                                    </Space>
                                ),
                            },
                        ]}
                    />
                </SectionCard>

                <Modal
                    title={editing ? "编辑记忆策略" : "新建记忆策略"}
                    open={modalOpen}
                    onCancel={() => setModalOpen(false)}
                    footer={null}
                    destroyOnHidden
                    width={640}
                >
                    <Card size="small" variant="borderless">
                        <Form<PolicyForm> form={form} layout="vertical" onFinish={onSubmit}>
                            {modalOpen &&
                            ((watchedRetrieval === "VECTOR" || watchedRetrieval === "HYBRID") ||
                                watchedWrite === "ASSISTANT_SUMMARY") ? (
                                <Alert
                                    type="warning"
                                    showIcon
                                    style={{marginBottom: 16}}
                                    message="能力提示"
                                    description={
                                        <ul style={{margin: 0, paddingLeft: 20}}>
                                            {(watchedRetrieval === "VECTOR" || watchedRetrieval === "HYBRID") && (
                                                <li key="ret">
                                                    检索模式为 {watchedRetrieval}
                                                    ：请配置下方<strong>公共向量库 Profile</strong>
                                                    （与知识库相同）；向量写入/检索复用 Milvus 或 Qdrant。
                                                    未配置完整时运行时<strong>降级为关键词</strong>检索。
                                                </li>
                                            )}
                                            {watchedWrite === "ASSISTANT_SUMMARY" && (
                                                <li key="w">
                                                    写入模式为 ASSISTANT_SUMMARY：当前为<strong>本地粗略摘要</strong>
                                                    （字数上限与句读截断），非 LLM 语义摘要。
                                                </li>
                                            )}
                                        </ul>
                                    }
                                />
                            ) : null}
                            <Form.Item name="name" label="策略名称" rules={[{required: true, message: "必填"}]}>
                                <Input placeholder="例如：客服线统一记忆"/>
                            </Form.Item>
                            <Form.Item name="description" label="说明">
                                <Input.TextArea rows={2} placeholder="给人看的边界说明（可选）"/>
                            </Form.Item>
                            <Form.Item name="strategyKind" label="记忆目的（strategyKind）">
                                <Select
                                    options={[...STRATEGY_OPTIONS].map((o) => ({
                                        value: o.value,
                                        label: `${o.label} — ${o.hint}`,
                                    }))}
                                />
                            </Form.Item>
                            <Form.Item name="scopeKind" label="作用域语义（scopeKind）">
                                <Select
                                    options={[...SCOPE_KIND_OPTIONS].map((o) => ({
                                        value: o.value,
                                        label: `${o.label} — ${o.hint}`,
                                    }))}
                                />
                            </Form.Item>
                            <Form.Item
                                name="ownerScope"
                                label="owner_scope（隔离键字符串）"
                                rules={[{required: true, message: "必填"}]}
                                extra="全局唯一；CUSTOM_NAMESPACE 下由你自行约定前缀（租户/业务线/场景）。"
                            >
                                <Input disabled={!!editing} placeholder="例如 tenant:1:assistant:orders"/>
                            </Form.Item>
                            <Form.Item name="retrievalMode" label="检索模式（retrievalMode）">
                                <Select
                                    options={[...RETRIEVAL_OPTIONS].map((o) => ({
                                        value: o.value,
                                        label: `${o.label} — ${o.hint}`,
                                    }))}
                                />
                            </Form.Item>
                            <Form.Item name="topK" label="单次召回条数 topK">
                                <InputNumber min={1} max={32} style={{width: "100%"}}/>
                            </Form.Item>
                            <Form.Item name="writeMode" label="写入模式（writeMode）">
                                <Select
                                    options={[...WRITE_MODE_OPTIONS].map((o) => ({
                                        value: o.value,
                                        label: `${o.label} — ${o.hint}`,
                                    }))}
                                />
                            </Form.Item>
                            <Form.Item name="writeBackOnDuplicate" label="写入时重复正文">
                                <Select
                                    options={[
                                        {value: "skip", label: "skip：完全相同则跳过"},
                                        {value: "upsert", label: "upsert：刷新 updated_at"},
                                    ]}
                                />
                            </Form.Item>
                            {(watchedRetrieval === "VECTOR" || watchedRetrieval === "HYBRID") && (
                                <>
                                    <Form.Item
                                        name="vectorStoreProfileId"
                                        label="向量库 Profile（vectorStoreProfileId）"
                                        extra="与知识库集合一致；新建 VECTOR/HYBRID 时必填。"
                                        rules={[
                                            {
                                                required: !editing,
                                                message: "请选择向量库 Profile",
                                            },
                                        ]}
                                    >
                                        <Select
                                            allowClear
                                            showSearch
                                            optionFilterProp="label"
                                            placeholder="公共向量库"
                                            disabled={!!editing && !!watchedClearVector}
                                            options={vectorProfiles.map((p) => ({
                                                value: p.id,
                                                label: `${p.name} (${p.id})`,
                                            }))}
                                        />
                                    </Form.Item>
                                    <Form.Item
                                        name="vectorCollectionName"
                                        label="物理集合名（可选）"
                                        extra="覆盖 profile 中的 collectionName；留空则后端使用 mem_pol_{策略id}。"
                                    >
                                        <Input
                                            disabled={!!editing && !!watchedClearVector}
                                            placeholder="可选"
                                        />
                                    </Form.Item>
                                    <Form.Item name="vectorMinScore" label="向量相似度下限（0～1）">
                                        <InputNumber
                                            min={0}
                                            max={1}
                                            step={0.05}
                                            style={{width: "100%"}}
                                            disabled={!!editing && !!watchedClearVector}
                                            placeholder="默认 0.15"
                                        />
                                    </Form.Item>
                                    {editing ? (
                                        <Form.Item
                                            name="clearVectorLink"
                                            valuePropName="checked"
                                            style={{marginBottom: 8}}
                                        >
                                            <Checkbox>清除向量库绑定与配置</Checkbox>
                                        </Form.Item>
                                    ) : null}
                                </>
                            )}
                            {editing ? (
                                <Form.Item
                                    name="clearRoughSummaryMaxChars"
                                    valuePropName="checked"
                                    style={{marginBottom: 8}}
                                >
                                    <Checkbox>清除已保存的摘要上限（运行时使用平台默认 480）</Checkbox>
                                </Form.Item>
                            ) : null}
                            <Form.Item
                                name="roughSummaryMaxChars"
                                label="粗略摘要最大字符数（roughSummaryMaxChars）"
                                extra={
                                    editing
                                        ? "勾选上方「清除」则忽略本框并清空库中配置。否则填写数字则更新；留空则不修改已保存值。"
                                        : "仅在写入模式为 ASSISTANT_SUMMARY 时生效；16～8192；留空则使用平台默认 480。"
                                }
                            >
                                <InputNumber
                                    min={16}
                                    max={8192}
                                    placeholder="默认 480"
                                    style={{width: "100%"}}
                                    disabled={!!editing && !!watchedClearRough}
                                />
                            </Form.Item>
                            <Form.Item>
                                <Space>
                                    <Button type="primary" htmlType="submit" loading={saving}>
                                        保存
                                    </Button>
                                    <Button onClick={() => setModalOpen(false)}>取消</Button>
                                </Space>
                            </Form.Item>
                        </Form>
                    </Card>
                </Modal>
            </PageShell>
        </AppLayout>
    );
}
