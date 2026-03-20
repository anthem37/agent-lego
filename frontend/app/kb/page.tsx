"use client";

import {Button, Form, Input, InputNumber, message, Space, Table, Typography} from "antd";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {SectionCard} from "@/components/SectionCard";
import {request} from "@/lib/api/request";
import {tablePaginationFriendly} from "@/lib/table-pagination";
import {stringifyPretty} from "@/lib/json";

type KbIngestResponse = { documentId: string };

type KbChunkDto = {
    id: string;
    documentId: string;
    chunkIndex: number;
    content: string;
    metadata?: Record<string, unknown>;
    createdAt?: string;
};

type KbQueryResponse = { chunks: KbChunkDto[] };

type IngestForm = {
    kbKey: string;
    name: string;
    content: string;
    chunkSize: number;
    overlap: number;
};

type QueryForm = {
    kbKey: string;
    queryText: string;
    topK: number;
};

export default function KbPage() {
    const [ingesting, setIngesting] = React.useState(false);
    const [documentId, setDocumentId] = React.useState<string | null>(null);
    const [querying, setQuerying] = React.useState(false);
    const [chunks, setChunks] = React.useState<KbChunkDto[]>([]);
    const [error, setError] = React.useState<unknown>(null);
    const [ingestForm] = Form.useForm<IngestForm>();
    const [queryForm] = Form.useForm<QueryForm>();

    async function onIngest(values: IngestForm) {
        setError(null);
        setDocumentId(null);
        setIngesting(true);
        try {
            const resp = await request<KbIngestResponse>("/kb/documents", {method: "POST", body: values});
            setDocumentId(resp.documentId);
            message.success("知识库文档写入成功");
        } catch (e) {
            setError(e);
        } finally {
            setIngesting(false);
        }
    }

    async function onQuery(values: QueryForm) {
        setError(null);
        setQuerying(true);
        try {
            const resp = await request<KbQueryResponse>("/kb/query", {method: "POST", body: values});
            setChunks(resp.chunks ?? []);
            message.success(`查询完成，命中 ${resp.chunks?.length ?? 0} 个分片`);
        } catch (e) {
            setError(e);
        } finally {
            setQuerying(false);
        }
    }

    return (
        <AppLayout>
            <Space orientation="vertical" size={16} style={{width: "100%"}}>
                <PageHeaderBlock title="知识库" subtitle="写入文档并分片，然后按 queryText 检索 chunk。"/>

                <ErrorAlert error={error}/>

                <SectionCard title="ingest（写入文档）">
                    <Form<IngestForm>
                        form={ingestForm}
                        layout="vertical"
                        onFinish={onIngest}
                        initialValues={{chunkSize: 800, overlap: 100}}
                    >
                        <Form.Item name="kbKey" label="kbKey" rules={[{required: true, message: "请输入 kbKey"}]}>
                            <Input placeholder="例如 default"/>
                        </Form.Item>
                        <Form.Item name="name" label="name" rules={[{required: true, message: "请输入 name"}]}>
                            <Input placeholder="例如 产品手册"/>
                        </Form.Item>
                        <Form.Item name="content" label="content" rules={[{required: true, message: "请输入 content"}]}>
                            <Input.TextArea rows={6} placeholder="文档内容（纯文本）"/>
                        </Form.Item>
                        <Space size={16} wrap>
                            <Form.Item name="chunkSize" label="chunkSize" rules={[{required: true, message: "必填"}]}>
                                <InputNumber min={100}/>
                            </Form.Item>
                            <Form.Item name="overlap" label="overlap" rules={[{required: true, message: "必填"}]}>
                                <InputNumber min={0}/>
                            </Form.Item>
                        </Space>
                        <Form.Item>
                            <Button type="primary" htmlType="submit" loading={ingesting}>
                                ingest
                            </Button>
                        </Form.Item>
                    </Form>

                    {documentId ? (
                        <Typography.Paragraph style={{marginBottom: 0}}>
                            documentId：<Typography.Text code>{documentId}</Typography.Text>
                        </Typography.Paragraph>
                    ) : null}
                </SectionCard>

                <SectionCard title="query（检索）">
                    <Form<QueryForm>
                        form={queryForm}
                        layout="inline"
                        onFinish={onQuery}
                        initialValues={{topK: 5}}
                        style={{rowGap: 8}}
                    >
                        <Form.Item name="kbKey" label="kbKey" rules={[{required: true, message: "必填"}]}>
                            <Input style={{width: 180}}/>
                        </Form.Item>
                        <Form.Item name="queryText" label="queryText" rules={[{required: true, message: "必填"}]}>
                            <Input style={{width: 360}}/>
                        </Form.Item>
                        <Form.Item name="topK" label="topK" rules={[{required: true, message: "必填"}]}>
                            <InputNumber min={1} style={{width: 120}}/>
                        </Form.Item>
                        <Form.Item>
                            <Button type="primary" htmlType="submit" loading={querying}>
                                查询
                            </Button>
                        </Form.Item>
                    </Form>

                    <div style={{marginTop: 12}}>
                        <Table<KbChunkDto>
                            rowKey="id"
                            dataSource={chunks}
                            pagination={tablePaginationFriendly()}
                            columns={[
                                {
                                    title: "chunkIndex",
                                    dataIndex: "chunkIndex",
                                    width: 90,
                                },
                                {
                                    title: "content",
                                    dataIndex: "content",
                                    render: (v: string) => <Typography.Text>{v}</Typography.Text>,
                                },
                                {
                                    title: "metadata",
                                    dataIndex: "metadata",
                                    render: (v: Record<string, unknown>) => (
                                        <pre
                                            style={{margin: 0, whiteSpace: "pre-wrap"}}>{stringifyPretty(v ?? {})}</pre>
                                    ),
                                },
                                {title: "createdAt", dataIndex: "createdAt", width: 180},
                            ]}
                        />
                    </div>
                </SectionCard>
            </Space>
        </AppLayout>
    );
}

