"use client";

import {Button, Form, Input, message, Space, Typography} from "antd";
import Link from "next/link";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {JsonTextArea} from "@/components/JsonTextArea";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {SectionCard} from "@/components/SectionCard";
import {request} from "@/lib/api/request";
import {parseJsonArray} from "@/lib/json";

type EvalCaseDto = { input: string; expectedOutput: string };

type CreateEvaluationForm = {
    name: string;
    agentId: string;
    modelId: string;
    casesJson: string;
};

type RunEvaluationResponse = { runId: string; status: string };

export default function EvaluationsPage() {
    const [creating, setCreating] = React.useState(false);
    const [createdId, setCreatedId] = React.useState<string | null>(null);
    const [running, setRunning] = React.useState(false);
    const [runResp, setRunResp] = React.useState<RunEvaluationResponse | null>(null);
    const [error, setError] = React.useState<unknown>(null);
    const [form] = Form.useForm<CreateEvaluationForm>();

    async function onCreate(values: CreateEvaluationForm) {
        setError(null);
        setCreatedId(null);
        setRunResp(null);
        setCreating(true);
        try {
            const cases = parseJsonArray(values.casesJson) as EvalCaseDto[];
            const id = await request<string>("/evaluations", {
                method: "POST",
                body: {
                    name: values.name,
                    agentId: values.agentId,
                    modelId: values.modelId,
                    cases,
                },
            });
            setCreatedId(id);
            message.success("评测创建成功");
        } catch (e) {
            setError(e);
        } finally {
            setCreating(false);
        }
    }

    async function onRun() {
        if (!createdId) {
            return;
        }
        setError(null);
        setRunning(true);
        setRunResp(null);
        try {
            const resp = await request<RunEvaluationResponse>(`/evaluations/${createdId}/runs`, {method: "POST"});
            setRunResp(resp);
            message.success("评测运行已触发");
        } catch (e) {
            setError(e);
        } finally {
            setRunning(false);
        }
    }

    return (
        <AppLayout>
            <Space orientation="vertical" size={16} style={{width: "100%"}}>
                <PageHeaderBlock title="评测" subtitle="创建评测（cases + agentId + modelId），并触发运行。"/>

                <ErrorAlert error={error}/>

                <SectionCard title="创建评测">
                    <Form<CreateEvaluationForm>
                        form={form}
                        layout="vertical"
                        onFinish={onCreate}
                        initialValues={{
                            casesJson: JSON.stringify([{input: "你好", expectedOutput: "你好"}], null, 2),
                        }}
                    >
                        <Form.Item name="name" label="name" rules={[{required: true, message: "请输入 name"}]}>
                            <Input placeholder="例如 基础正确性评测"/>
                        </Form.Item>
                        <Form.Item name="agentId" label="agentId" rules={[{required: true, message: "请输入 agentId"}]}>
                            <Input placeholder="从 /agents 创建后得到的 id"/>
                        </Form.Item>
                        <Form.Item name="modelId" label="modelId" rules={[{required: true, message: "请输入 modelId"}]}>
                            <Input placeholder="从 /models 创建后得到的 id"/>
                        </Form.Item>
                        <Form.Item name="casesJson" label="cases（JSON 数组）"
                                   rules={[{required: true, message: "请输入 cases"}]}>
                            <JsonTextArea rows={12}/>
                        </Form.Item>
                        <Form.Item>
                            <Button type="primary" htmlType="submit" loading={creating}>
                                创建
                            </Button>
                        </Form.Item>
                    </Form>

                    {createdId ? (
                        <Typography.Paragraph style={{marginBottom: 0}}>
                            evaluationId：<Typography.Text code>{createdId}</Typography.Text>
                        </Typography.Paragraph>
                    ) : null}
                </SectionCard>

                <SectionCard title="运行评测">
                    <Space orientation="vertical" size={12} style={{width: "100%"}}>
                        <Button type="primary" onClick={onRun} loading={running} disabled={!createdId}>
                            运行（runs）
                        </Button>
                        {runResp ? (
                            <Typography.Paragraph style={{marginBottom: 0}}>
                                runId：<Typography.Text code>{runResp.runId}</Typography.Text>（{runResp.status}）{" "}
                                <Link href={`/evaluations/runs/${runResp.runId}`}>去查看</Link>
                            </Typography.Paragraph>
                        ) : (
                            <Typography.Text type="secondary">尚未触发</Typography.Text>
                        )}
                    </Space>
                </SectionCard>
            </Space>
        </AppLayout>
    );
}

