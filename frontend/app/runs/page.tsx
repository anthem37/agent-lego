"use client";

import {Button, Card, Form, Input, Space, Typography} from "antd";
import {useRouter} from "next/navigation";
import React from "react";

import {AppLayout} from "@/components/AppLayout";

type RunQueryForm = {
    runId: string;
};

export default function RunsIndexPage() {
    const router = useRouter();
    const [form] = Form.useForm<RunQueryForm>();

    function onGo(values: RunQueryForm) {
        router.push(`/runs/${values.runId}`);
    }

    return (
        <AppLayout>
            <Space orientation="vertical" size={16} style={{width: "100%"}}>
                <div>
                    <Typography.Title level={3} style={{margin: 0}}>
                        运行查询
                    </Typography.Title>
                    <Typography.Text type="secondary">输入 runId，查询工作流运行状态与输出。</Typography.Text>
                </div>

                <Card title="按 runId 查询">
                    <Form form={form} layout="vertical" onFinish={onGo}>
                        <Form.Item name="runId" label="runId" rules={[{required: true, message: "请输入 runId"}]}>
                            <Input placeholder="从 /workflows/{id}/runs 返回的 runId"/>
                        </Form.Item>
                        <Form.Item>
                            <Button type="primary" htmlType="submit">
                                查询
                            </Button>
                        </Form.Item>
                    </Form>
                </Card>
            </Space>
        </AppLayout>
    );
}

