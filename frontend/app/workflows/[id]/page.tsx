"use client";

import {Button, Card, Descriptions, Form, Input, Space, Typography} from "antd";
import Link from "next/link";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {request} from "@/lib/api/request";
import {stringifyPretty} from "@/lib/json";

type WorkflowDto = {
    id: string;
    name: string;
    definition?: Record<string, unknown>;
    createdAt?: string;
};

type RunWorkflowForm = {
    input: string;
};

type RunWorkflowResponse = {
    runId: string;
    status: string;
};

export default function WorkflowDetailPage(props: { params: Promise<{ id: string }> }) {
    const [workflow, setWorkflow] = React.useState<WorkflowDto | null>(null);
    const [loading, setLoading] = React.useState(false);
    const [running, setRunning] = React.useState(false);
    const [runResp, setRunResp] = React.useState<RunWorkflowResponse | null>(null);
    const [error, setError] = React.useState<unknown>(null);
    const [form] = Form.useForm<RunWorkflowForm>();

    React.useEffect(() => {
        let cancelled = false;
        setLoading(true);
        setError(null);
        void props.params.then(async ({id}) => {
            try {
                const data = await request<WorkflowDto>(`/workflows/${id}`);
                if (!cancelled) {
                    setWorkflow(data);
                }
            } catch (e) {
                if (!cancelled) {
                    setError(e);
                }
            } finally {
                if (!cancelled) {
                    setLoading(false);
                }
            }
        });
        return () => {
            cancelled = true;
        };
    }, [props.params]);

    async function onRun(values: RunWorkflowForm) {
        if (!workflow) {
            return;
        }
        setError(null);
        setRunning(true);
        setRunResp(null);
        try {
            const data = await request<RunWorkflowResponse>(`/workflows/${workflow.id}/runs`, {
                method: "POST",
                body: values,
            });
            setRunResp(data);
        } catch (e) {
            setError(e);
        } finally {
            setRunning(false);
        }
    }

    return (
        <AppLayout>
            <Space orientation="vertical" size={16} style={{width: "100%"}}>
                <div>
                    <Typography.Title level={3} style={{margin: 0}}>
                        工作流详情
                    </Typography.Title>
                    <Typography.Text type="secondary">
                        {workflow ? (
                            <>
                                ID：<Typography.Text code>{workflow.id}</Typography.Text>
                            </>
                        ) : (
                            "加载中…"
                        )}
                    </Typography.Text>
                </div>

                <ErrorAlert error={error}/>

                <Card title="定义" loading={loading}>
                    {workflow ? (
                        <Descriptions column={1} size="small">
                            <Descriptions.Item label="name">{workflow.name}</Descriptions.Item>
                            <Descriptions.Item label="createdAt">{workflow.createdAt ?? "-"}</Descriptions.Item>
                            <Descriptions.Item label="definition">
                                <pre style={{
                                    margin: 0,
                                    whiteSpace: "pre-wrap"
                                }}>{stringifyPretty(workflow.definition ?? {})}</pre>
                            </Descriptions.Item>
                        </Descriptions>
                    ) : (
                        <Typography.Text type="secondary">未加载到数据</Typography.Text>
                    )}
                </Card>

                <Card title="触发运行">
                    <Form<RunWorkflowForm> form={form} layout="vertical" onFinish={onRun}>
                        <Form.Item name="input" label="input" rules={[{required: true, message: "请输入 input"}]}>
                            <Input.TextArea rows={4} placeholder="作为工作流首步输入"/>
                        </Form.Item>
                        <Form.Item>
                            <Button type="primary" htmlType="submit" loading={running} disabled={!workflow}>
                                运行
                            </Button>
                        </Form.Item>
                    </Form>

                    {runResp ? (
                        <Typography.Paragraph style={{marginBottom: 0}}>
                            runId：<Typography.Text code>{runResp.runId}</Typography.Text>（{runResp.status}）{" "}
                            <Link href={`/runs/${runResp.runId}`}>去查看</Link>
                        </Typography.Paragraph>
                    ) : (
                        <Typography.Text type="secondary">尚未触发</Typography.Text>
                    )}
                </Card>
            </Space>
        </AppLayout>
    );
}

