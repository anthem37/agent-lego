"use client";

import {DatabaseOutlined, DeleteOutlined, EditOutlined, PlusOutlined} from "@ant-design/icons";
import {Button, Card, Form, Input, InputNumber, message, Modal, Select, Space, Table, Typography,} from "antd";
import Link from "next/link";
import React from "react";

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
};

export default function MemoryPoliciesPage() {
    const [error, setError] = React.useState<unknown>(null);
    const [loading, setLoading] = React.useState(false);
    const [rows, setRows] = React.useState<MemoryPolicyDto[]>([]);
    const [modalOpen, setModalOpen] = React.useState(false);
    const [editing, setEditing] = React.useState<MemoryPolicyDto | null>(null);
    const [saving, setSaving] = React.useState(false);
    const [form] = Form.useForm<PolicyForm>();

    const load = React.useCallback(async () => {
        setError(null);
        setLoading(true);
        try {
            const data = await listMemoryPolicies();
            setRows(Array.isArray(data) ? data : []);
        } catch (e) {
            setError(e);
            setRows([]);
        } finally {
            setLoading(false);
        }
    }, []);

    React.useEffect(() => {
        void load();
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
        });
        setModalOpen(true);
    }

    async function onSubmit(values: PolicyForm) {
        setSaving(true);
        setError(null);
        try {
            const body = {
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
            if (editing) {
                await updateMemoryPolicy(editing.id, body);
                message.success("策略已更新");
            } else {
                await createMemoryPolicy(body);
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
                        <Button type="primary" icon={<PlusOutlined/>} onClick={openCreate}>
                            新建策略
                        </Button>
                    }
                >
                    <Table<MemoryPolicyDto>
                        size="small"
                        rowKey="id"
                        loading={loading}
                        dataSource={rows}
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
                                                            await deleteMemoryPolicy(p.id);
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
