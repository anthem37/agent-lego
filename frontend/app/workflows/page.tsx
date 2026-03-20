"use client";

import {Button, Form, Input, message, Space, Typography} from "antd";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {JsonTextArea} from "@/components/JsonTextArea";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {SectionCard} from "@/components/SectionCard";
import {request} from "@/lib/api/request";
import {parseJsonObject} from "@/lib/json";

type CreateWorkflowForm = {
    name: string;
    definitionJson?: string;
};

export default function WorkflowsPage() {
    const [creating, setCreating] = React.useState(false);
    const [createdId, setCreatedId] = React.useState<string | null>(null);
    const [error, setError] = React.useState<unknown>(null);
    const [form] = Form.useForm<CreateWorkflowForm>();

    async function onCreate(values: CreateWorkflowForm) {
        setError(null);
        setCreatedId(null);
        setCreating(true);
        try {
            const definition = values.definitionJson ? parseJsonObject(values.definitionJson) : undefined;
            const id = await request<string>("/workflows", {
                method: "POST",
                body: {
                    name: values.name,
                    definition,
                },
            });
            setCreatedId(id);
            message.success("工作流创建成功");
        } catch (e) {
            setError(e);
        } finally {
            setCreating(false);
        }
    }

    return (
        <AppLayout>
            <Space orientation="vertical" size={16} style={{width: "100%"}}>
                <PageHeaderBlock
                    title="工作流"
                    subtitle="创建工作流定义（definition JSON），再到详情页触发运行并用 runId 查询。"
                />

                <ErrorAlert error={error}/>

                <SectionCard title="创建工作流">
                    <Form form={form} layout="vertical" onFinish={onCreate}>
                        <Form.Item name="name" label="name" rules={[{required: true, message: "请输入 name"}]}>
                            <Input placeholder="例如 多智能体并行总结"/>
                        </Form.Item>
                        <Form.Item name="definitionJson" label="definition（JSON，可选）">
                            <JsonTextArea
                                rows={12}
                                sample={{
                                    mode: "sequential",
                                    steps: [{agentId: "agentId", modelId: "modelId"}],
                                }}
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

