"use client";

import {Button, Form, Input, message, Select, Space, Table, Typography} from "antd";
import Link from "next/link";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {JsonTextArea} from "@/components/JsonTextArea";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {SectionCard} from "@/components/SectionCard";
import {request} from "@/lib/api/request";
import {parseJsonObject} from "@/lib/json";

type ToolDto = {
    id: string;
    toolType: string;
    name: string;
    definition?: Record<string, unknown>;
    createdAt?: string;
};

type CreateToolForm = {
    toolType: "LOCAL" | "MCP";
    name: string;
    definitionJson?: string;
};

export default function ToolsPage() {
    const [tools, setTools] = React.useState<ToolDto[]>([]);
    const [loading, setLoading] = React.useState(false);
    const [creating, setCreating] = React.useState(false);
    const [createdId, setCreatedId] = React.useState<string | null>(null);
    const [error, setError] = React.useState<unknown>(null);
    const [form] = Form.useForm<CreateToolForm>();

    async function reload() {
        setError(null);
        setLoading(true);
        try {
            const list = await request<ToolDto[]>("/tools");
            setTools(list);
        } catch (e) {
            setError(e);
        } finally {
            setLoading(false);
        }
    }

    React.useEffect(() => {
        void reload();
    }, []);

    async function onCreate(values: CreateToolForm) {
        setError(null);
        setCreatedId(null);
        setCreating(true);
        try {
            const definition = values.definitionJson ? parseJsonObject(values.definitionJson) : undefined;
            const id = await request<string>("/tools", {
                method: "POST",
                body: {
                    toolType: values.toolType,
                    name: values.name,
                    definition,
                },
            });
            setCreatedId(id);
            message.success("工具创建成功");
            await reload();
        } catch (e) {
            setError(e);
        } finally {
            setCreating(false);
        }
    }

    return (
        <AppLayout>
            <Space orientation="vertical" size={16} style={{width: "100%"}}>
                <PageHeaderBlock title="工具" subtitle="注册 LOCAL/MCP 工具，并支持 test-call。"/>

                <ErrorAlert error={error}/>

                <SectionCard
                    title="工具列表"
                    extra={
                        <Space>
                            <Button onClick={() => void reload()} loading={loading}>
                                刷新
                            </Button>
                        </Space>
                    }
                >
                    <Table<ToolDto>
                        rowKey="id"
                        loading={loading}
                        dataSource={tools}
                        pagination={{pageSize: 10}}
                        columns={[
                            {
                                title: "name",
                                dataIndex: "name",
                                render: (v: string, r) => <Link href={`/tools/${r.id}`}>{v}</Link>,
                            },
                            {title: "toolType", dataIndex: "toolType"},
                            {
                                title: "id",
                                dataIndex: "id",
                                render: (v: string) => <Typography.Text code>{v}</Typography.Text>,
                            },
                            {title: "createdAt", dataIndex: "createdAt"},
                        ]}
                    />
                </SectionCard>

                <SectionCard title="创建工具">
                    <Form form={form} layout="vertical" onFinish={onCreate} initialValues={{toolType: "LOCAL"}}>
                        <Form.Item name="toolType" label="toolType"
                                   rules={[{required: true, message: "请选择 toolType"}]}>
                            <Select
                                options={[
                                    {value: "LOCAL", label: "LOCAL"},
                                    {value: "MCP", label: "MCP"},
                                ]}
                            />
                        </Form.Item>
                        <Form.Item name="name" label="name" rules={[{required: true, message: "请输入 name"}]}>
                            <Input placeholder="例如 echo / now / myTool"/>
                        </Form.Item>
                        <Form.Item name="definitionJson" label="definition（JSON，可选）">
                            <JsonTextArea
                                rows={10}
                                placeholder='例如 {"description":"...","inputSchema":{...}}'
                                sample={{description: "示例工具", inputSchema: {type: "object", properties: {}}}}
                            />
                        </Form.Item>
                        <Form.Item>
                            <Space>
                                <Button type="primary" htmlType="submit" loading={creating}>
                                    创建
                                </Button>
                                <Button
                                    onClick={() => {
                                        form.resetFields();
                                        setCreatedId(null);
                                        setError(null);
                                    }}
                                >
                                    重置
                                </Button>
                            </Space>
                        </Form.Item>
                    </Form>

                    {createdId ? (
                        <Typography.Paragraph style={{marginTop: 12, marginBottom: 0}}>
                            已创建：<Typography.Text code>{createdId}</Typography.Text>
                        </Typography.Paragraph>
                    ) : null}
                </SectionCard>
            </Space>
        </AppLayout>
    );
}

