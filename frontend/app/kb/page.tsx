"use client";

import {Button, Form, Input, message, Popconfirm, Select, Space, Table, Typography} from "antd";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {SectionCard} from "@/components/SectionCard";
import {request} from "@/lib/api/request";
import {type ModelOptionRow, toModelSelectOptions} from "@/lib/model-select-options";
import {tablePaginationFriendly} from "@/lib/table-pagination";

type KbCollectionDto = {
    id: string;
    name: string;
    description?: string;
    embeddingModelId: string;
    embeddingDims?: number;
    createdAt?: string;
};

type KbDocumentDto = {
    id: string;
    collectionId: string;
    title: string;
    status: string;
    errorMessage?: string;
    createdAt?: string;
};

type CreateCollectionForm = {
    name: string;
    description?: string;
    embeddingModelId: string;
};

type IngestForm = {
    title: string;
    body: string;
};

export default function KnowledgeBasePage() {
    const [error, setError] = React.useState<unknown>(null);
    const [modelRows, setModelRows] = React.useState<ModelOptionRow[]>([]);
    const [collections, setCollections] = React.useState<KbCollectionDto[]>([]);
    const [documents, setDocuments] = React.useState<KbDocumentDto[]>([]);
    const [loadingCollections, setLoadingCollections] = React.useState(false);
    const [loadingDocs, setLoadingDocs] = React.useState(false);
    const [creating, setCreating] = React.useState(false);
    const [ingesting, setIngesting] = React.useState(false);
    const [deletingCollectionId, setDeletingCollectionId] = React.useState<string | null>(null);
    const [deletingDocumentId, setDeletingDocumentId] = React.useState<string | null>(null);
    const [selectedCollectionId, setSelectedCollectionId] = React.useState<string | undefined>();
    const [collectionForm] = Form.useForm<CreateCollectionForm>();
    const [ingestForm] = Form.useForm<IngestForm>();

    const embeddingModelRows = React.useMemo(
        () =>
            modelRows.filter(
                (m) =>
                    m.chatProvider === false ||
                    (m.chatProvider !== true && m.provider.toUpperCase().includes("EMBEDDING")),
            ),
        [modelRows],
    );

    const loadCollections = React.useCallback(async () => {
        setLoadingCollections(true);
        setError(null);
        try {
            const data = await request<KbCollectionDto[]>("/kb/collections");
            setCollections(Array.isArray(data) ? data : []);
        } catch (e) {
            setError(e);
        } finally {
            setLoadingCollections(false);
        }
    }, []);

    React.useEffect(() => {
        let cancelled = false;
        void request<ModelOptionRow[]>("/models")
            .then((d) => {
                if (!cancelled) {
                    setModelRows(Array.isArray(d) ? d : []);
                }
            })
            .catch(() => {
                if (!cancelled) {
                    setModelRows([]);
                }
            });
        return () => {
            cancelled = true;
        };
    }, []);

    React.useEffect(() => {
        void loadCollections();
    }, [loadCollections]);

    React.useEffect(() => {
        if (!selectedCollectionId) {
            setDocuments([]);
            return;
        }
        let cancelled = false;
        setLoadingDocs(true);
        void request<KbDocumentDto[]>(`/kb/collections/${selectedCollectionId}/documents`)
            .then((d) => {
                if (!cancelled) {
                    setDocuments(Array.isArray(d) ? d : []);
                }
            })
            .catch((e) => {
                if (!cancelled) {
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
        };
    }, [selectedCollectionId]);

    async function onCreateCollection(values: CreateCollectionForm) {
        setCreating(true);
        setError(null);
        try {
            await request<string>("/kb/collections", {
                method: "POST",
                body: {
                    name: values.name,
                    description: values.description ?? "",
                    embeddingModelId: values.embeddingModelId,
                },
            });
            message.success("集合已创建");
            collectionForm.resetFields();
            await loadCollections();
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
            await request<string>(`/kb/collections/${selectedCollectionId}/documents`, {
                method: "POST",
                body: {title: values.title, body: values.body},
            });
            message.success("文档已写入并完成向量化");
            ingestForm.resetFields(["title", "body"]);
            const data = await request<KbDocumentDto[]>(`/kb/collections/${selectedCollectionId}/documents`);
            setDocuments(Array.isArray(data) ? data : []);
        } catch (e) {
            setError(e);
        } finally {
            setIngesting(false);
        }
    }

    async function onDeleteCollection(collectionId: string) {
        setDeletingCollectionId(collectionId);
        setError(null);
        try {
            const del = await request<{ agentsPolicyUpdated: number }>(`/kb/collections/${collectionId}`, {
                method: "DELETE",
            });
            const n = typeof del?.agentsPolicyUpdated === "number" ? del.agentsPolicyUpdated : 0;
            message.success(
                n > 0
                    ? `已删除集合；已更新 ${n} 个智能体的知识库策略`
                    : "已删除集合（级联文档与分片）",
            );
            if (selectedCollectionId === collectionId) {
                setSelectedCollectionId(undefined);
            }
            await loadCollections();
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
            await request<null>(`/kb/collections/${selectedCollectionId}/documents/${documentId}`, {
                method: "DELETE",
            });
            message.success("已删除文档");
            const data = await request<KbDocumentDto[]>(`/kb/collections/${selectedCollectionId}/documents`);
            setDocuments(Array.isArray(data) ? data : []);
        } catch (e) {
            setError(e);
        } finally {
            setDeletingDocumentId(null);
        }
    }

    return (
        <AppLayout>
            <Space orientation="vertical" size={16} style={{width: "100%"}}>
                <PageHeaderBlock
                    title="知识库"
                    subtitle="v3：集合绑定文本嵌入模型配置（下拉里已过滤聊天模型）→ 分片入库 → 智能体通过 knowledge_base_policy 启用 RAG。"
                />

                <ErrorAlert error={error}/>

                <SectionCard title="新建集合">
                    <Form form={collectionForm} layout="vertical" onFinish={onCreateCollection}>
                        <Form.Item name="name" label="名称" rules={[{required: true, message: "请输入名称"}]}>
                            <Input placeholder="例如 产品说明"/>
                        </Form.Item>
                        <Form.Item name="description" label="描述（可选）">
                            <Input placeholder="简短说明"/>
                        </Form.Item>
                        <Form.Item
                            name="embeddingModelId"
                            label="向量化模型配置"
                            rules={[{required: true, message: "请选择 embedding 模型配置"}]}
                        >
                            <Select
                                showSearch
                                placeholder="须为平台中的 embedding 类型模型配置"
                                options={toModelSelectOptions(embeddingModelRows)}
                                popupMatchSelectWidth={520}
                                filterOption={(input, option) => {
                                    const st = (option as { searchText?: string }).searchText ?? "";
                                    const q = input.trim().toLowerCase();
                                    return !q || st.includes(q);
                                }}
                            />
                        </Form.Item>
                        <Form.Item>
                            <Button type="primary" htmlType="submit" loading={creating}>
                                创建集合
                            </Button>
                        </Form.Item>
                    </Form>
                </SectionCard>

                <SectionCard title="集合与文档">
                    <Space orientation="vertical" size={12} style={{width: "100%"}}>
                        <Typography.Text type="secondary">
                            选择集合后可查看文档列表并写入新文档（同步分片 + 向量化）。
                        </Typography.Text>
                        <Select
                            allowClear
                            placeholder="选择知识库集合"
                            style={{maxWidth: 480}}
                            value={selectedCollectionId}
                            onChange={(v) => setSelectedCollectionId(v)}
                            options={collections.map((c) => ({
                                label: `${c.name} (${c.id})`,
                                value: c.id,
                            }))}
                        />
                        <Table<KbCollectionDto>
                            size="small"
                            rowKey="id"
                            loading={loadingCollections}
                            dataSource={collections}
                            pagination={tablePaginationFriendly()}
                            columns={[
                                {title: "ID", dataIndex: "id", width: 200, ellipsis: true},
                                {title: "名称", dataIndex: "name"},
                                {title: "输出维", dataIndex: "embeddingDims", width: 88},
                                {title: "embeddingModelId", dataIndex: "embeddingModelId", ellipsis: true},
                                {title: "创建时间", dataIndex: "createdAt", width: 200},
                                {
                                    title: "操作",
                                    key: "actions",
                                    width: 100,
                                    render: (_, row) => (
                                        <Popconfirm
                                            title="删除整个集合？"
                                            description="将级联删除其下所有文档与向量分片，并从引用该集合的智能体策略中移除对应 collectionId。"
                                            okText="删除"
                                            cancelText="取消"
                                            okButtonProps={{danger: true}}
                                            onConfirm={() => void onDeleteCollection(row.id)}
                                        >
                                            <Button
                                                type="link"
                                                danger
                                                size="small"
                                                loading={deletingCollectionId === row.id}
                                            >
                                                删除
                                            </Button>
                                        </Popconfirm>
                                    ),
                                },
                            ]}
                        />

                        <Typography.Title level={5} style={{marginTop: 16, marginBottom: 0}}>
                            文档
                        </Typography.Title>
                        <Table<KbDocumentDto>
                            size="small"
                            rowKey="id"
                            loading={loadingDocs}
                            dataSource={documents}
                            pagination={tablePaginationFriendly()}
                            columns={[
                                {title: "ID", dataIndex: "id", width: 200, ellipsis: true},
                                {title: "标题", dataIndex: "title"},
                                {title: "状态", dataIndex: "status", width: 100},
                                {
                                    title: "错误",
                                    dataIndex: "errorMessage",
                                    ellipsis: true,
                                    render: (t: string) => t || "—",
                                },
                                {
                                    title: "操作",
                                    key: "docActions",
                                    width: 100,
                                    render: (_, row) => (
                                        <Popconfirm
                                            title="删除该文档？"
                                            description="将删除文档及其全部分片向量。"
                                            okText="删除"
                                            cancelText="取消"
                                            okButtonProps={{danger: true}}
                                            onConfirm={() => void onDeleteDocument(row.id)}
                                        >
                                            <Button
                                                type="link"
                                                danger
                                                size="small"
                                                disabled={!selectedCollectionId}
                                                loading={deletingDocumentId === row.id}
                                            >
                                                删除
                                            </Button>
                                        </Popconfirm>
                                    ),
                                },
                            ]}
                        />

                        <Form form={ingestForm} layout="vertical" onFinish={onIngest}>
                            <Form.Item name="title" label="文档标题" rules={[{required: true, message: "请输入标题"}]}>
                                <Input placeholder="例如 发布说明 2025Q1"/>
                            </Form.Item>
                            <Form.Item name="body" label="正文" rules={[{required: true, message: "请输入正文"}]}>
                                <Input.TextArea rows={10} placeholder="纯文本，将按窗口分片并写入向量"/>
                            </Form.Item>
                            <Form.Item>
                                <Button type="primary" htmlType="submit" loading={ingesting}
                                        disabled={!selectedCollectionId}>
                                    写入文档
                                </Button>
                            </Form.Item>
                        </Form>
                    </Space>
                </SectionCard>
            </Space>
        </AppLayout>
    );
}
