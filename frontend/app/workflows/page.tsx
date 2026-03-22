"use client";

import {BranchesOutlined} from "@ant-design/icons";
import {Button, Form, Input, message, Radio, Space, Typography} from "antd";
import Link from "next/link";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {PageShell} from "@/components/PageShell";
import {SectionCard} from "@/components/SectionCard";
import {createWorkflow} from "@/lib/workflows/api";

type StepRow = { agentId: string; modelId: string };

type CreateWorkflowForm = {
    name: string;
    /** single：单智能体；steps：多步编排 */
    orchestration: "single" | "steps";
    singleAgentId?: string;
    singleModelId?: string;
    stepMode?: "sequential" | "parallel";
    steps?: StepRow[];
};

function buildDefinition(values: CreateWorkflowForm): Record<string, unknown> | undefined {
    if (values.orchestration === "single") {
        const agentId = (values.singleAgentId ?? "").trim();
        const modelId = (values.singleModelId ?? "").trim();
        if (!agentId || !modelId) {
            return undefined;
        }
        return {agentId, modelId};
    }
    const raw = values.steps ?? [];
    const steps = raw
        .map((s) => ({
            agentId: (s.agentId ?? "").trim(),
            modelId: (s.modelId ?? "").trim(),
        }))
        .filter((s) => s.agentId && s.modelId);
    if (steps.length === 0) {
        return undefined;
    }
    const mode = values.stepMode === "parallel" ? "parallel" : "sequential";
    return {mode, steps};
}

export default function WorkflowsPage() {
    const [creating, setCreating] = React.useState(false);
    const [createdId, setCreatedId] = React.useState<string | null>(null);
    const [error, setError] = React.useState<unknown>(null);
    const [form] = Form.useForm<CreateWorkflowForm>();

    const orchestration = Form.useWatch("orchestration", form);

    async function onCreate(values: CreateWorkflowForm) {
        setError(null);
        setCreatedId(null);
        setCreating(true);
        try {
            const definition = buildDefinition(values);
            const id = await createWorkflow({
                name: values.name,
                definition,
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
            <PageShell>
                <PageHeaderBlock
                    icon={<BranchesOutlined/>}
                    title="工作流"
                    subtitle={
                        <>
                            将多个智能体按顺序或并行编排。请先在
                            <Link href="/agents"> 智能体 </Link>与
                            <Link href="/models"> 模型 </Link>中准备好编号；运行后可在
                            <Link href="/runs"> 运行查询 </Link>追踪。
                        </>
                    }
                />

                <ErrorAlert error={error}/>

                <SectionCard title="创建工作流">
                    <Form<CreateWorkflowForm>
                        form={form}
                        layout="vertical"
                        onFinish={onCreate}
                        initialValues={{
                            orchestration: "single",
                            stepMode: "sequential",
                            steps: [{agentId: "", modelId: ""}],
                        }}
                    >
                        <Form.Item name="name" label="名称" rules={[{required: true, message: "请输入名称"}]}>
                            <Input placeholder="例如 多智能体并行总结"/>
                        </Form.Item>

                        <Form.Item name="orchestration" label="编排方式" rules={[{required: true}]}>
                            <Radio.Group>
                                <Radio.Button value="single">单智能体</Radio.Button>
                                <Radio.Button value="steps">多步（steps）</Radio.Button>
                            </Radio.Group>
                        </Form.Item>

                        {orchestration === "single" ? (
                            <>
                                <Form.Item
                                    name="singleAgentId"
                                    label="智能体 ID（agentId）"
                                    rules={[{required: true, message: "请填写智能体 ID"}]}
                                >
                                    <Input placeholder="从「智能体」页创建后复制"/>
                                </Form.Item>
                                <Form.Item
                                    name="singleModelId"
                                    label="模型 ID（modelId）"
                                    rules={[{required: true, message: "请填写模型 ID"}]}
                                >
                                    <Input placeholder="从「模型」页创建后复制"/>
                                </Form.Item>
                            </>
                        ) : (
                            <>
                                <Form.Item name="stepMode" label="多步模式（mode）" rules={[{required: true}]}>
                                    <Radio.Group>
                                        <Radio.Button value="sequential">顺序（上一步输出作为下一步输入）</Radio.Button>
                                        <Radio.Button value="parallel">并行（各步同一输入）</Radio.Button>
                                    </Radio.Group>
                                </Form.Item>
                                <Typography.Paragraph type="secondary" style={{fontSize: 12, marginBottom: 8}}>
                                    每一步填写要调用的智能体与模型；至少保留一行有效配置。
                                </Typography.Paragraph>
                                <Form.List name="steps">
                                    {(fields, {add, remove}) => (
                                        <>
                                            {fields.map(({key, name, ...restField}) => (
                                                <Space
                                                    key={key}
                                                    align="start"
                                                    style={{
                                                        display: "flex",
                                                        marginBottom: 12,
                                                        flexWrap: "wrap",
                                                        rowGap: 8,
                                                    }}
                                                >
                                                    <Form.Item
                                                        {...restField}
                                                        name={[name, "agentId"]}
                                                        label={name === 0 ? "agentId" : undefined}
                                                        rules={[{required: true, message: "必填"}]}
                                                    >
                                                        <Input placeholder="agentId" style={{width: 220}}/>
                                                    </Form.Item>
                                                    <Form.Item
                                                        {...restField}
                                                        name={[name, "modelId"]}
                                                        label={name === 0 ? "modelId" : undefined}
                                                        rules={[{required: true, message: "必填"}]}
                                                    >
                                                        <Input placeholder="modelId" style={{width: 220}}/>
                                                    </Form.Item>
                                                    <Button type="link" onClick={() => remove(name)}>
                                                        删除本步
                                                    </Button>
                                                </Space>
                                            ))}
                                            <Button type="dashed" onClick={() => add({agentId: "", modelId: ""})} block>
                                                添加一步
                                            </Button>
                                        </>
                                    )}
                                </Form.List>
                            </>
                        )}

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
            </PageShell>
        </AppLayout>
    );
}
