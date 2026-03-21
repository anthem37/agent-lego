"use client";

import {Button, Card, Descriptions, Form, Input, Select, Space, Typography} from "antd";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {JsonTextArea} from "@/components/JsonTextArea";
import {type ModelOptionRow, toModelSelectOptions} from "@/lib/model-select-options";
import {request} from "@/lib/api/request";
import {parseJsonObject, stringifyPretty} from "@/lib/json";

type AgentDto = {
    id: string;
    name: string;
    systemPrompt: string;
    modelId: string;
    modelDisplayName?: string;
    modelProvider?: string;
    modelModelKey?: string;
    modelConfigSummary?: string;
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
    const [modelRows, setModelRows] = React.useState<ModelOptionRow[]>([]);
    const [loading, setLoading] = React.useState(false);
    const [running, setRunning] = React.useState(false);
    const [runOut, setRunOut] = React.useState<RunAgentResponse | null>(null);
    const [error, setError] = React.useState<unknown>(null);
    const [form] = Form.useForm<RunAgentForm>();

    const chatModelRows = React.useMemo(
        () => modelRows.filter((m) => m.chatProvider !== false),
        [modelRows],
    );

    React.useEffect(() => {
        let cancelled = false;
        void request<ModelOptionRow[]>("/models")
            .then((d) => {
                if (!cancelled) {
                    setModelRows(Array.isArray(d) ? d : []);
                }
            })
            .catch(() => {
                if (!cancelled) {
                    setModelRows([]);
                }
            });
        return () => {
            cancelled = true;
        };
    }, []);

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
            const body: Record<string, unknown> = {
                input: values.input,
            };
            const mid = values.modelId?.trim();
            if (mid) {
                body.modelId = mid;
            }
            if (options !== undefined) {
                body.options = options;
            }
            const data = await request<RunAgentResponse>(`/agents/${agent.id}/run`, {
                method: "POST",
                body,
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
                            <Descriptions.Item label="默认模型配置">
                                <Space orientation="vertical" size={4}>
                                    <Typography.Text strong>{agent.modelDisplayName ?? "—"}</Typography.Text>
                                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                                        {agent.modelProvider && agent.modelModelKey
                                            ? `${agent.modelProvider} / ${agent.modelModelKey}`
                                            : null}
                                    </Typography.Text>
                                    {agent.modelConfigSummary ? (
                                        <Typography.Text type="secondary" style={{fontSize: 12}} ellipsis>
                                            参数摘要：{agent.modelConfigSummary}
                                        </Typography.Text>
                                    ) : null}
                                    <Typography.Text code copyable>
                                        {agent.modelId}
                                    </Typography.Text>
                                </Space>
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
                                <pre style={{
                                    margin: 0,
                                    whiteSpace: "pre-wrap"
                                }}>{stringifyPretty(agent.knowledgeBasePolicy ?? {})}</pre>
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
                        <Form.Item name="modelId" label="运行时使用模型配置（可选覆盖）">
                            <Select
                                allowClear
                                showSearch
                                placeholder={
                                    agent?.modelDisplayName
                                        ? `默认：${agent.modelDisplayName}`
                                        : "默认使用上方绑定配置；可在此临时切换"
                                }
                                options={toModelSelectOptions(chatModelRows)}
                                popupMatchSelectWidth={520}
                                filterOption={(input, option) => {
                                    const st = (option as { searchText?: string }).searchText ?? "";
                                    const q = input.trim().toLowerCase();
                                    return !q || st.includes(q);
                                }}
                            />
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

