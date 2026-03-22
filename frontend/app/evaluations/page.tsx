"use client";

import {ExperimentOutlined} from "@ant-design/icons";
import {Button, Form, Input, message, Space, Typography} from "antd";
import Link from "next/link";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {PageShell} from "@/components/PageShell";
import {SectionCard} from "@/components/SectionCard";
import {createEvaluation, triggerEvaluationRun} from "@/lib/evaluations/api";
import type {EvalCaseDto, RunEvaluationResponse} from "@/lib/evaluations/types";

type CreateEvaluationForm = {
    name: string;
    agentId: string;
    modelId: string;
    cases: EvalCaseDto[];
};

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
            const cases = (values.cases ?? [])
                .map((c) => ({
                    input: (c.input ?? "").trim(),
                    expectedOutput: (c.expectedOutput ?? "").trim(),
                }))
                .filter((c) => c.input || c.expectedOutput);
            if (cases.length === 0) {
                message.warning("请至少填写一条用例（输入与期望输出）");
                setCreating(false);
                return;
            }
            const id = await createEvaluation({
                name: values.name,
                agentId: values.agentId,
                modelId: values.modelId,
                cases,
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
            const resp = await triggerEvaluationRun(createdId);
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
            <PageShell>
                <PageHeaderBlock
                    icon={<ExperimentOutlined/>}
                    title="评测"
                    subtitle={
                        <>
                            用例化验证智能体在指定模型下的表现。请先准备好
                            <Link href="/agents"> 智能体 </Link>与
                            <Link href="/models"> 模型 </Link>。
                        </>
                    }
                />

                <ErrorAlert error={error}/>

                <SectionCard title="创建评测">
                    <Form<CreateEvaluationForm>
                        form={form}
                        layout="vertical"
                        onFinish={onCreate}
                        initialValues={{
                            cases: [{input: "你好", expectedOutput: "你好"}],
                        }}
                    >
                        <Form.Item name="name" label="名称" rules={[{required: true, message: "请输入名称"}]}>
                            <Input placeholder="例如 基础正确性评测"/>
                        </Form.Item>
                        <Form.Item name="agentId" label="智能体 ID"
                                   rules={[{required: true, message: "请输入智能体 ID"}]}>
                            <Input placeholder="从智能体页创建后复制"/>
                        </Form.Item>
                        <Form.Item name="modelId" label="模型 ID" rules={[{required: true, message: "请输入模型 ID"}]}>
                            <Input placeholder="从模型页创建后复制"/>
                        </Form.Item>
                        <Form.Item label="用例列表（cases）" required>
                            <Typography.Paragraph type="secondary" style={{marginBottom: 8, fontSize: 12}}>
                                逐条填写用户输入与期望输出，可添加多行。
                            </Typography.Paragraph>
                            <Form.List name="cases">
                                {(fields, {add, remove}) => (
                                    <>
                                        {fields.map(({key, name, ...restField}) => (
                                            <Space
                                                key={key}
                                                align="start"
                                                style={{
                                                    display: "flex",
                                                    marginBottom: 12,
                                                    width: "100%",
                                                    flexWrap: "wrap",
                                                }}
                                            >
                                                <Form.Item
                                                    {...restField}
                                                    name={[name, "input"]}
                                                    label={name === 0 ? "用户输入" : undefined}
                                                    rules={[{required: true, message: "请输入输入"}]}
                                                    style={{flex: 1, minWidth: 200, marginBottom: 0}}
                                                >
                                                    <Input.TextArea rows={2} placeholder="用户输入"/>
                                                </Form.Item>
                                                <Form.Item
                                                    {...restField}
                                                    name={[name, "expectedOutput"]}
                                                    label={name === 0 ? "期望助手输出" : undefined}
                                                    rules={[{required: true, message: "请输入期望输出"}]}
                                                    style={{flex: 1, minWidth: 200, marginBottom: 0}}
                                                >
                                                    <Input.TextArea rows={2} placeholder="期望助手输出"/>
                                                </Form.Item>
                                                <Button type="link" onClick={() => remove(name)}>
                                                    删除
                                                </Button>
                                            </Space>
                                        ))}
                                        <Button type="dashed" onClick={() => add({input: "", expectedOutput: ""})}
                                                block>
                                            添加用例
                                        </Button>
                                    </>
                                )}
                            </Form.List>
                        </Form.Item>
                        <Form.Item>
                            <Button type="primary" htmlType="submit" loading={creating}>
                                创建
                            </Button>
                        </Form.Item>
                    </Form>

                    {createdId ? (
                        <Typography.Paragraph style={{marginBottom: 0}}>
                            评测编号：<Typography.Text code>{createdId}</Typography.Text>
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
            </PageShell>
        </AppLayout>
    );
}
