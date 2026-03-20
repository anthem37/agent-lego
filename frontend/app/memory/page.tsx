"use client";

import {Button, Form, Input, InputNumber, message, Space, Table, Typography} from "antd";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {JsonTextArea} from "@/components/JsonTextArea";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {SectionCard} from "@/components/SectionCard";
import {request} from "@/lib/api/request";
import {parseJsonObject, stringifyPretty} from "@/lib/json";

type MemoryItemDto = {
    id: string;
    ownerScope: string;
    content: string;
    metadata?: Record<string, unknown>;
    createdAt?: string;
};

type MemoryQueryResponse = {
    items: MemoryItemDto[];
};

type CreateMemoryForm = {
    ownerScope: string;
    content: string;
    metadataJson: string;
};

type QueryMemoryForm = {
    ownerScope: string;
    queryText: string;
    topK: number;
};

export default function MemoryPage() {
    const [creating, setCreating] = React.useState(false);
    const [createdId, setCreatedId] = React.useState<string | null>(null);
    const [querying, setQuerying] = React.useState(false);
    const [items, setItems] = React.useState<MemoryItemDto[]>([]);
    const [error, setError] = React.useState<unknown>(null);
    const [createForm] = Form.useForm<CreateMemoryForm>();
    const [queryForm] = Form.useForm<QueryMemoryForm>();

    async function onCreate(values: CreateMemoryForm) {
        setError(null);
        setCreatedId(null);
        setCreating(true);
        try {
            const id = await request<string>("/memory/items", {
                method: "POST",
                body: {
                    ownerScope: values.ownerScope,
                    content: values.content,
                    metadata: parseJsonObject(values.metadataJson),
                },
            });
            setCreatedId(id);
            message.success("记忆写入成功");
        } catch (e) {
            setError(e);
        } finally {
            setCreating(false);
        }
    }

    async function onQuery(values: QueryMemoryForm) {
        setError(null);
        setQuerying(true);
        try {
            const resp = await request<MemoryQueryResponse>("/memory/query", {method: "POST", body: values});
            setItems(resp.items ?? []);
            message.success(`查询完成，命中 ${resp.items?.length ?? 0} 条`);
        } catch (e) {
            setError(e);
        } finally {
            setQuerying(false);
        }
    }

    return (
        <AppLayout>
            <Space orientation="vertical" size={16} style={{width: "100%"}}>
                <PageHeaderBlock title="记忆" subtitle="写入 memory item，并按 ownerScope + queryText 检索。"/>

                <ErrorAlert error={error}/>

                <SectionCard title="写入">
                    <Form<CreateMemoryForm>
                        form={createForm}
                        layout="vertical"
                        onFinish={onCreate}
                        initialValues={{metadataJson: "{}"}}
                    >
                        <Form.Item name="ownerScope" label="ownerScope"
                                   rules={[{required: true, message: "请输入 ownerScope"}]}>
                            <Input placeholder="例如 demoUser"/>
                        </Form.Item>
                        <Form.Item name="content" label="content" rules={[{required: true, message: "请输入 content"}]}>
                            <Input.TextArea rows={3} placeholder="要写入的记忆内容"/>
                        </Form.Item>
                        <Form.Item name="metadataJson" label="metadata（JSON）"
                                   rules={[{required: true, message: "请输入 metadata"}]}>
                            <JsonTextArea rows={8} sample={{source: "ui", tag: "demo"}}/>
                        </Form.Item>
                        <Form.Item>
                            <Button type="primary" htmlType="submit" loading={creating}>
                                写入
                            </Button>
                        </Form.Item>
                    </Form>
                    {createdId ? (
                        <Typography.Paragraph style={{marginBottom: 0}}>
                            已写入：<Typography.Text code>{createdId}</Typography.Text>
                        </Typography.Paragraph>
                    ) : null}
                </SectionCard>

                <SectionCard title="检索">
                    <Form<QueryMemoryForm>
                        form={queryForm}
                        layout="inline"
                        onFinish={onQuery}
                        initialValues={{topK: 5}}
                        style={{rowGap: 8}}
                    >
                        <Form.Item name="ownerScope" label="ownerScope" rules={[{required: true, message: "必填"}]}>
                            <Input style={{width: 200}}/>
                        </Form.Item>
                        <Form.Item name="queryText" label="queryText" rules={[{required: true, message: "必填"}]}>
                            <Input style={{width: 320}}/>
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
                        <Table<MemoryItemDto>
                            rowKey="id"
                            dataSource={items}
                            pagination={{pageSize: 10}}
                            columns={[
                                {
                                    title: "id",
                                    dataIndex: "id",
                                    render: (v: string) => <Typography.Text code>{v}</Typography.Text>,
                                },
                                {title: "ownerScope", dataIndex: "ownerScope"},
                                {title: "createdAt", dataIndex: "createdAt"},
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
                            ]}
                        />
                    </div>
                </SectionCard>
            </Space>
        </AppLayout>
    );
}

