"use client";

import {BranchesOutlined} from "@ant-design/icons";
import {Button, Descriptions, Form, Input, Typography} from "antd";
import Link from "next/link";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {PageShell} from "@/components/PageShell";
import {SectionCard} from "@/components/SectionCard";
import {stringifyPretty} from "@/lib/json";
import {getWorkflow, runWorkflow} from "@/lib/workflows/api";
import type {RunWorkflowForm, RunWorkflowResponse, WorkflowDto} from "@/lib/workflows/types";

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
                const data = await getWorkflow(id);
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
            const data = await runWorkflow(workflow.id, values);
            setRunResp(data);
        } catch (e) {
            setError(e);
        } finally {
            setRunning(false);
        }
    }

    return (
        <AppLayout>
            <PageShell>
                <PageHeaderBlock
                    icon={<BranchesOutlined/>}
                    backHref="/workflows"
                    title="工作流详情"
                    subtitle={
                        workflow ? (
                            <>
                                ID：<Typography.Text code>{workflow.id}</Typography.Text>
                            </>
                        ) : (
                            "加载中…"
                        )
                    }
                />

                <ErrorAlert error={error}/>

                <SectionCard title="定义" loading={loading}>
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
                </SectionCard>

                <SectionCard title="触发运行">
                    <Form<RunWorkflowForm> form={form} layout="vertical" onFinish={onRun}>
                        <Form.Item name="input" label="首步输入内容" rules={[{required: true, message: "请输入内容"}]}>
                            <Input.TextArea rows={4} placeholder="作为工作流第一步的用户输入"/>
                        </Form.Item>
                        <Form.Item>
                            <Button type="primary" htmlType="submit" loading={running} disabled={!workflow}>
                                运行
                            </Button>
                        </Form.Item>
                    </Form>

                    {runResp ? (
                        <Typography.Paragraph style={{marginBottom: 0}}>
                            运行编号：<Typography.Text code>{runResp.runId}</Typography.Text>（{runResp.status}）{" "}
                            <Link href={`/runs/${runResp.runId}`}>查看执行详情</Link>
                        </Typography.Paragraph>
                    ) : (
                        <Typography.Text type="secondary">尚未触发</Typography.Text>
                    )}
                </SectionCard>
            </PageShell>
        </AppLayout>
    );
}

