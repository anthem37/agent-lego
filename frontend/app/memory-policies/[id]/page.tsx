"use client";

import {DeleteOutlined, ReloadOutlined, ThunderboltOutlined} from "@ant-design/icons";
import {
    Alert,
    Button,
    Card,
    Descriptions,
    Form,
    Input,
    InputNumber,
    message,
    Space,
    Table,
    Tag,
    Tooltip,
    Typography
} from "antd";
import Link from "next/link";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {PageShell} from "@/components/PageShell";
import {SectionCard} from "@/components/SectionCard";
import {isAbortError} from "@/lib/api/isAbortError";
import {DEFAULT_REQUEST_TIMEOUT_MS} from "@/lib/api/request";
import {
    type AgentRefDto,
    createMemoryItem,
    deleteMemoryItem,
    getMemoryPolicy,
    listMemoryItems,
    listReferencingAgents,
    type MemoryItemDto,
    type MemoryPolicyDto,
    reindexMemoryPolicyVectors,
} from "@/lib/memory-policies/api";
import {retrievalLabel, strategyLabel, writeModeLabel} from "@/lib/memory-policies/semantics";

type SearchForm = {
    q?: string;
    limit?: number;
};

type CreateItemForm = {
    content: string;
};

function memoryItemMetaTags(meta?: Record<string, unknown>): React.ReactNode {
    if (!meta || Object.keys(meta).length === 0) {
        return <Typography.Text type="secondary">—</Typography.Text>;
    }
    const summaryKind = meta.summaryKind;
    const strategyKind = meta.strategyKind;
    const ns = meta.memoryNamespace;
    const roughMax = meta.roughSummaryMaxChars;
    const tags: React.ReactNode[] = [];
    if (summaryKind === "ROUGH_CHAR_CAP") {
        tags.push(
            <Tag key="rough" color="orange">
                粗略摘要
            </Tag>,
        );
    } else if (typeof summaryKind === "string" && summaryKind.length > 0) {
        tags.push(
            <Tag key="sk" color="default">
                {summaryKind}
            </Tag>,
        );
    }
    if (typeof strategyKind === "string" && strategyKind.length > 0) {
        tags.push(
            <Tag key="stk" color="blue">
                {strategyKind}
            </Tag>,
        );
    }
    if (typeof ns === "string" && ns.length > 0) {
        tags.push(
            <Tag key="ns" color="geekblue">
                ns:{ns}
            </Tag>,
        );
    }
    if (typeof roughMax === "number" && Number.isFinite(roughMax)) {
        tags.push(
            <Tag key="rm" color="purple">
                cap:{roughMax}
            </Tag>,
        );
    }
    if (tags.length === 0) {
        return <Typography.Text type="secondary">—</Typography.Text>;
    }
    return <Space size={4} wrap>{tags}</Space>;
}

export default function MemoryPolicyDetailPage(props: { params: Promise<{ id: string }> }) {
    const [policyId, setPolicyId] = React.useState<string | null>(null);
    const [policy, setPolicy] = React.useState<MemoryPolicyDto | null>(null);
    const [refAgents, setRefAgents] = React.useState<AgentRefDto[]>([]);
    const [items, setItems] = React.useState<MemoryItemDto[]>([]);
    const [loading, setLoading] = React.useState(false);
    const [reindexing, setReindexing] = React.useState(false);
    const [error, setError] = React.useState<unknown>(null);
    const [searchForm] = Form.useForm<SearchForm>();
    const [createForm] = Form.useForm<CreateItemForm>();

    React.useEffect(() => {
        let cancelled = false;
        void props.params.then(async ({id}) => {
            if (!cancelled) {
                setPolicyId(id);
            }
        });
        return () => {
            cancelled = true;
        };
    }, [props.params]);

    const loadItems = React.useCallback(async (signal?: AbortSignal) => {
        if (!policyId) {
            return;
        }
        setLoading(true);
        setError(null);
        const fetchOpts = {signal, timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS};
        try {
            const q = searchForm.getFieldValue("q")?.trim();
            const limit = searchForm.getFieldValue("limit");
            const rows = await listMemoryItems(
                policyId,
                {
                    ...(q ? {q, orderByTrgm: true} : {}),
                    ...(typeof limit === "number" ? {limit} : {}),
                },
                fetchOpts,
            );
            setItems(Array.isArray(rows) ? rows : []);
        } catch (e) {
            if (!isAbortError(e)) {
                setError(e);
                setItems([]);
            }
        } finally {
            setLoading(false);
        }
    }, [policyId, searchForm]);

    React.useEffect(() => {
        if (!policyId) {
            return;
        }
        const ac = new AbortController();
        const fetchOpts = {signal: ac.signal, timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS};
        setError(null);
        void getMemoryPolicy(policyId, fetchOpts)
            .then((p) => setPolicy(p))
            .catch((e) => {
                if (!isAbortError(e)) {
                    setError(e);
                    setPolicy(null);
                }
            });
        void listReferencingAgents(policyId, fetchOpts)
            .then((rows) => setRefAgents(Array.isArray(rows) ? rows : []))
            .catch(() => {
                if (!ac.signal.aborted) {
                    setRefAgents([]);
                }
            });
        return () => ac.abort();
    }, [policyId]);

    React.useEffect(() => {
        if (!policyId) {
            return;
        }
        const ac = new AbortController();
        void loadItems(ac.signal);
        return () => ac.abort();
    }, [policyId, loadItems]);

    async function onCreateItem(values: CreateItemForm) {
        if (!policyId) {
            return;
        }
        setError(null);
        try {
            await createMemoryItem(policyId, {content: values.content.trim()}, {timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS});
            message.success("已写入条目");
            createForm.resetFields();
            await loadItems();
        } catch (e) {
            setError(e);
        }
    }

    async function onReindexVectors() {
        if (!policyId) {
            return;
        }
        setError(null);
        setReindexing(true);
        try {
            const r = await reindexMemoryPolicyVectors(policyId, {timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS});
            message.success(`向量重索引完成：${r.indexedCount} 条`);
        } catch (e) {
            setError(e);
        } finally {
            setReindexing(false);
        }
    }

    return (
        <AppLayout>
            <PageShell>
                <PageHeaderBlock
                    backHref="/memory-policies"
                    title="记忆策略详情"
                    subtitle={
                        policy ? (
                            <>
                                <Typography.Text code>{policy.id}</Typography.Text>
                                <span style={{marginLeft: 8}}>{policy.name}</span>
                            </>
                        ) : (
                            "加载中…"
                        )
                    }
                />

                <ErrorAlert error={error}/>

                {policy?.implementationWarnings && policy.implementationWarnings.length > 0 ? (
                    <Alert
                        type="warning"
                        showIcon
                        style={{marginBottom: 16}}
                        message="当前实现与配置说明"
                        description={
                            <ul style={{margin: 0, paddingLeft: 20}}>
                                {policy.implementationWarnings.map((w) => (
                                    <li key={w}>{w}</li>
                                ))}
                            </ul>
                        }
                    />
                ) : null}

                <SectionCard title="策略参数">
                    {policy ? (
                        <Descriptions column={1} size="small" labelStyle={{width: 160}}>
                            <Descriptions.Item label="记忆目的">{strategyLabel(policy.strategyKind)}</Descriptions.Item>
                            <Descriptions.Item label="作用域语义">{policy.scopeKind ?? "—"}</Descriptions.Item>
                            <Descriptions.Item label="owner_scope">
                                <Typography.Text code>{policy.ownerScope}</Typography.Text>
                            </Descriptions.Item>
                            <Descriptions.Item
                                label="检索模式">{retrievalLabel(policy.retrievalMode)}</Descriptions.Item>
                            <Descriptions.Item label="topK">{policy.topK ?? "—"}</Descriptions.Item>
                            <Descriptions.Item label="写入模式">{writeModeLabel(policy.writeMode)}</Descriptions.Item>
                            <Descriptions.Item label="粗略摘要上限（字符）">
                                {typeof policy.roughSummaryMaxChars === "number"
                                    ? policy.roughSummaryMaxChars
                                    : "默认 480（未单独配置）"}
                            </Descriptions.Item>
                            <Descriptions.Item label="向量库 Profile">
                                {policy.vectorStoreProfileId ? (
                                    <Typography.Text code>{policy.vectorStoreProfileId}</Typography.Text>
                                ) : (
                                    "—"
                                )}
                            </Descriptions.Item>
                            <Descriptions.Item label="向量链路">
                                {policy.retrievalMode === "VECTOR" || policy.retrievalMode === "HYBRID"
                                    ? policy.vectorLinkConfigured
                                        ? "已配置（可外置检索）"
                                        : "未配置完整（运行时降级关键词）"
                                    : "—"}
                            </Descriptions.Item>
                            <Descriptions.Item label="vectorMinScore">
                                {typeof policy.vectorMinScore === "number" ? policy.vectorMinScore : "—"}
                            </Descriptions.Item>
                            <Descriptions.Item label="重复正文">{policy.writeBackOnDuplicate ?? "—"}</Descriptions.Item>
                            <Descriptions.Item label="说明">{policy.description || "—"}</Descriptions.Item>
                            <Descriptions.Item label="引用智能体数">
                                {typeof policy.referencingAgentCount === "number" ? policy.referencingAgentCount : "—"}
                            </Descriptions.Item>
                        </Descriptions>
                    ) : (
                        <Typography.Text type="secondary">未加载</Typography.Text>
                    )}
                    {policy && (policy.retrievalMode === "VECTOR" || policy.retrievalMode === "HYBRID") ? (
                        <div style={{marginTop: 12}}>
                            <Tooltip
                                title={
                                    policy.vectorLinkConfigured
                                        ? "对该策略下全部记忆条目重新写入外置向量索引"
                                        : "向量链路未配置完整，后端不会写入索引（请先配置 Profile / 集合）"
                                }
                            >
                                <Button
                                    icon={<ThunderboltOutlined/>}
                                    loading={reindexing}
                                    disabled={!policy.vectorLinkConfigured}
                                    onClick={() => void onReindexVectors()}
                                >
                                    重索引向量
                                </Button>
                            </Tooltip>
                        </div>
                    ) : null}
                </SectionCard>

                <SectionCard title="引用该策略的智能体">
                    {refAgents.length === 0 ? (
                        <Typography.Text type="secondary">暂无绑定</Typography.Text>
                    ) : (
                        <Space orientation="vertical" size={8} style={{width: "100%"}}>
                            {refAgents.map((a) => (
                                <div key={a.id}>
                                    <Link href={`/agents/${a.id}`}>
                                        <Typography.Text strong>{a.name}</Typography.Text>
                                    </Link>
                                    <Typography.Text type="secondary" style={{marginLeft: 8}} code>
                                        {a.id}
                                    </Typography.Text>
                                </div>
                            ))}
                        </Space>
                    )}
                </SectionCard>

                <SectionCard title="检索条目">
                    <Form<SearchForm>
                        form={searchForm}
                        layout="inline"
                        onFinish={() => void loadItems()}
                        initialValues={{limit: 20}}
                        style={{marginBottom: 16, flexWrap: "wrap", rowGap: 12}}
                    >
                        <Form.Item name="q" label="关键词">
                            <Input placeholder="匹配 content" allowClear style={{width: 220}}/>
                        </Form.Item>
                        <Form.Item name="limit" label="条数">
                            <InputNumber min={1} max={100} style={{width: 100}}/>
                        </Form.Item>
                        <Form.Item>
                            <Button type="primary" htmlType="submit" loading={loading} icon={<ReloadOutlined/>}>
                                查询
                            </Button>
                        </Form.Item>
                    </Form>
                    <Typography.Paragraph type="secondary" style={{fontSize: 12, marginBottom: 12}}>
                        填写关键词时，结果按 <Typography.Text code>word_similarity</Typography.Text>{" "}
                        排序（与智能体运行时关键词记忆检索一致）；仅浏览不传关键词时按时间倒序。
                    </Typography.Paragraph>

                    <Table<MemoryItemDto>
                        size="small"
                        rowKey="id"
                        loading={loading}
                        dataSource={items}
                        pagination={false}
                        scroll={{x: true}}
                        columns={[
                            {
                                title: "ID",
                                dataIndex: "id",
                                width: 120,
                                ellipsis: true,
                                render: (v: string) => <Typography.Text code copyable>{v}</Typography.Text>,
                            },
                            {
                                title: "内容",
                                dataIndex: "content",
                                ellipsis: true,
                            },
                            {
                                title: "元数据",
                                width: 220,
                                render: (_, row) => memoryItemMetaTags(row.metadata),
                            },
                            {
                                title: "创建时间",
                                dataIndex: "createdAt",
                                width: 200,
                            },
                            {
                                title: "操作",
                                width: 100,
                                render: (_, row) =>
                                    policyId ? (
                                        <Button
                                            type="link"
                                            danger
                                            size="small"
                                            icon={<DeleteOutlined/>}
                                            onClick={async () => {
                                                try {
                                                    await deleteMemoryItem(policyId, row.id, {
                                                        timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS,
                                                    });
                                                    message.success("已删除");
                                                    await loadItems();
                                                } catch (e) {
                                                    setError(e);
                                                }
                                            }}
                                        >
                                            删除
                                        </Button>
                                    ) : null,
                            },
                        ]}
                    />
                </SectionCard>

                <SectionCard title="手动写入条目">
                    <Typography.Paragraph type="secondary" style={{marginTop: 0}}>
                        调试或导入用；运行时写回仍由策略的 <Typography.Text code>writeBack</Typography.Text> 控制。
                    </Typography.Paragraph>
                    <Card size="small" style={{maxWidth: 720}}>
                        <Form<CreateItemForm> form={createForm} layout="vertical" onFinish={onCreateItem}>
                            <Form.Item name="content" label="正文" rules={[{required: true, message: "必填"}]}>
                                <Input.TextArea rows={4}/>
                            </Form.Item>
                            <Form.Item>
                                <Button type="primary" htmlType="submit" disabled={!policyId}>
                                    写入
                                </Button>
                            </Form.Item>
                        </Form>
                    </Card>
                </SectionCard>

                <SectionCard title="快捷跳转">
                    <Space wrap>
                        <Link href="/memory-policies">返回策略列表</Link>
                        <Typography.Text type="secondary">|</Typography.Text>
                        <Link href="/agents">智能体</Link>
                    </Space>
                </SectionCard>
            </PageShell>
        </AppLayout>
    );
}
