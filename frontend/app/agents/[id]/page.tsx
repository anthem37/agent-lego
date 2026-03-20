"use client";

import {Button, Card, Descriptions, Form, Input, Space, Typography} from "antd";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {JsonTextArea} from "@/components/JsonTextArea";
import {request} from "@/lib/api/request";
import {parseJsonObject, stringifyPretty} from "@/lib/json";

type AgentDto = {
    id: string;
    name: string;
    systemPrompt: string;
    modelId: string;
    toolIds?: string[];
    memoryPolicy?: Record<string, unknown>;
    knowledgeBasePolicy?: Record<string, unknown>;
    createdAt?: string;
};

type RunAgentForm = {
    modelId?: string;
    input: string;
    optionsJson?: string;
};

type RunAgentResponse = {
    output: string;
};

export default function AgentDetailPage(props: { params: Promise<{ id: string }> }) {
    const [agent, setAgent] = React.useState<AgentDto | null>(null);
    const [loading, setLoading] = React.useState(false);
    const [running, setRunning] = React.useState(false);
    const [runOut, setRunOut] = React.useState<RunAgentResponse | null>(null);
    const [error, setError] = React.useState<unknown>(null);
    const [form] = Form.useForm<RunAgentForm>();

    React.useEffect(() => {
        let cancelled = false;
        setLoading(true);
        setError(null);
        void props.params.then(async ({id}) => {
            try {
                const data = await request<AgentDto>(`/agents/${id}`);
                if (!cancelled) {
                    setAgent(data);
                    form.setFieldsValue({modelId: data.modelId});
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
    }, [form, props.params]);

    async function onRun(values: RunAgentForm) {
        if (!agent) {
            return;
        }
        setError(null);
        setRunning(true);
        setRunOut(null);
        try {
            const options = values.optionsJson ? parseJsonObject(values.optionsJson) : undefined;
            const data = await request<RunAgentResponse>(`/agents/${agent.id}/run`, {
                method: "POST",
                body: values,
                ...(options !== undefined ? {body: {modelId: values.modelId, input: values.input, options}} : {}),
            });
            setRunOut(data);
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
                        智能体详情
                    </Typography.Title>
                    <Typography.Text type="secondary">
                        {agent ? (
                            <>
                                ID：<Typography.Text code>{agent.id}</Typography.Text>
                            </>
                        ) : (
                            "加载中…"
                        )}
                    </Typography.Text>
                </div>

                <ErrorAlert error={error}/>

                <Card title="配置快照" loading={loading}>
                    {agent ? (
                        <Descriptions column={1} size="small">
                            <Descriptions.Item label="name">{agent.name}</Descriptions.Item>
                            <Descriptions.Item label="modelId">
                                <Typography.Text code>{agent.modelId}</Typography.Text>
                            </Descriptions.Item>
                            <Descriptions.Item label="createdAt">{agent.createdAt ?? "-"}</Descriptions.Item>
                            <Descriptions.Item label="toolIds">
                                <pre style={{
                                    margin: 0,
                                    whiteSpace: "pre-wrap"
                                }}>{stringifyPretty(agent.toolIds ?? [])}</pre>
                            </Descriptions.Item>
                            <Descriptions.Item label="memoryPolicy">
                                <pre style={{
                                    margin: 0,
                                    whiteSpace: "pre-wrap"
                                }}>{stringifyPretty(agent.memoryPolicy ?? {})}</pre>
                            </Descriptions.Item>
                            <Descriptions.Item label="knowledgeBasePolicy">
                <pre style={{margin: 0, whiteSpace: "pre-wrap"}}>
                  {stringifyPretty(agent.knowledgeBasePolicy ?? {})}
                </pre>
                            </Descriptions.Item>
                            <Descriptions.Item label="systemPrompt">
                                <pre style={{margin: 0, whiteSpace: "pre-wrap"}}>{agent.systemPrompt}</pre>
                            </Descriptions.Item>
                        </Descriptions>
                    ) : (
                        <Typography.Text type="secondary">未加载到数据</Typography.Text>
                    )}
                </Card>

                <Card title="运行（同步返回 output）">
                    <Form<RunAgentForm> form={form} layout="vertical" onFinish={onRun}>
                        <Form.Item name="modelId" label="modelId（可选覆盖）">
                            <Input placeholder={`默认使用绑定模型：${agent?.modelId ?? "-"}`}/>
                        </Form.Item>
                        <Form.Item name="optionsJson" label="options（JSON，可选覆盖）">
                            <JsonTextArea
                                rows={8}
                                sample={{
                                    temperature: 0.2,
                                    maxTokens: 256,
                                }}
                            />
                        </Form.Item>
                        <Form.Item name="input" label="input" rules={[{required: true, message: "请输入 input"}]}>
                            <Input.TextArea rows={4} placeholder="用户输入"/>
                        </Form.Item>
                        <Form.Item>
                            <Button type="primary" htmlType="submit" loading={running} disabled={!agent}>
                                运行
                            </Button>
                        </Form.Item>
                    </Form>
                    {runOut ? (
                        <pre style={{margin: 0, whiteSpace: "pre-wrap"}}>{runOut.output}</pre>
                    ) : (
                        <Typography.Text type="secondary">尚未运行</Typography.Text>
                    )}
                </Card>
            </Space>
        </AppLayout>
    );
}

