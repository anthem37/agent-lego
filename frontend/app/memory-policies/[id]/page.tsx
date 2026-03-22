"use client";

import {DeleteOutlined, ReloadOutlined} from "@ant-design/icons";
import {Button, Card, Descriptions, Form, Input, InputNumber, message, Space, Table, Typography} from "antd";
import Link from "next/link";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {PageShell} from "@/components/PageShell";
import {SectionCard} from "@/components/SectionCard";
import {
    type AgentRefDto,
    createMemoryItem,
    deleteMemoryItem,
    getMemoryPolicy,
    listMemoryItems,
    listReferencingAgents,
    type MemoryItemDto,
    type MemoryPolicyDto,
} from "@/lib/memory-policies/api";
import {retrievalLabel, strategyLabel, writeModeLabel} from "@/lib/memory-policies/semantics";

type SearchForm = {
    q?: string;
    limit?: number;
};

type CreateItemForm = {
    content: string;
};

export default function MemoryPolicyDetailPage(props: { params: Promise<{ id: string }> }) {
    const [policyId, setPolicyId] = React.useState<string | null>(null);
    const [policy, setPolicy] = React.useState<MemoryPolicyDto | null>(null);
    const [refAgents, setRefAgents] = React.useState<AgentRefDto[]>([]);
    const [items, setItems] = React.useState<MemoryItemDto[]>([]);
    const [loading, setLoading] = React.useState(false);
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

    const loadPolicy = React.useCallback(async () => {
        if (!policyId) {
            return;
        }
        setError(null);
        try {
            const p = await getMemoryPolicy(policyId);
            setPolicy(p);
        } catch (e) {
            setError(e);
            setPolicy(null);
        }
    }, [policyId]);

    const loadRefAgents = React.useCallback(async () => {
        if (!policyId) {
            return;
        }
        try {
            const rows = await listReferencingAgents(policyId);
            setRefAgents(Array.isArray(rows) ? rows : []);
        } catch {
            setRefAgents([]);
        }
    }, [policyId]);

    const loadItems = React.useCallback(async () => {
        if (!policyId) {
            return;
        }
        setLoading(true);
        setError(null);
        try {
            const q = searchForm.getFieldValue("q")?.trim();
            const limit = searchForm.getFieldValue("limit");
            const rows = await listMemoryItems(policyId, {
                ...(q ? {q} : {}),
                ...(typeof limit === "number" ? {limit} : {}),
            });
            setItems(Array.isArray(rows) ? rows : []);
        } catch (e) {
            setError(e);
            setItems([]);
        } finally {
            setLoading(false);
        }
    }, [policyId, searchForm]);

    React.useEffect(() => {
        void loadPolicy();
    }, [loadPolicy]);

    React.useEffect(() => {
        void loadRefAgents();
    }, [loadRefAgents]);

    React.useEffect(() => {
        if (policyId) {
            void loadItems();
        }
    }, [policyId, loadItems]);

    async function onCreateItem(values: CreateItemForm) {
        if (!policyId) {
            return;
        }
        setError(null);
        try {
            await createMemoryItem(policyId, {content: values.content.trim()});
            message.success("已写入条目");
            createForm.resetFields();
            await loadItems();
        } catch (e) {
            setError(e);
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
                            <Descriptions.Item label="重复正文">{policy.writeBackOnDuplicate ?? "—"}</Descriptions.Item>
                            <Descriptions.Item label="说明">{policy.description || "—"}</Descriptions.Item>
                            <Descriptions.Item label="引用智能体数">
                                {typeof policy.referencingAgentCount === "number" ? policy.referencingAgentCount : "—"}
                            </Descriptions.Item>
                        </Descriptions>
                    ) : (
                        <Typography.Text type="secondary">未加载</Typography.Text>
                    )}
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
                        onFinish={loadItems}
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
                                                    await deleteMemoryItem(policyId, row.id);
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
