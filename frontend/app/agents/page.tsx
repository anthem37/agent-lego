"use client";

import {RobotOutlined} from "@ant-design/icons";
import {Alert, Button, Divider, Form, Input, InputNumber, message, Select, Space, Switch, Typography,} from "antd";
import React from "react";

import {RuntimeKindPicker} from "@/components/agents/RuntimeKindPicker";
import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {PageShell} from "@/components/PageShell";
import {SectionCard} from "@/components/SectionCard";
import {DEFAULT_REQUEST_TIMEOUT_MS} from "@/lib/api/request";
import {createAgent} from "@/lib/agents/api";
import {buildUpsertAgentRequestBody} from "@/lib/agents/build-policy";
import {filterMemoryPolicySelectOption, filterModelSelectOption,} from "@/lib/agents/form-options";
import {AGENT_RUNTIME} from "@/lib/agents/runtime-kinds";
import type {UpsertAgentFormValues} from "@/lib/agents/types";
import {toModelSelectOptions} from "@/lib/model-select-options";
import {useAgentFormRefs} from "@/hooks/useAgentFormRefs";

export default function AgentsPage() {
    const [creating, setCreating] = React.useState(false);
    const [createdId, setCreatedId] = React.useState<string | null>(null);
    const [error, setError] = React.useState<unknown>(null);
    const [form] = Form.useForm<UpsertAgentFormValues>();

    const {
        loadingRefs,
        chatModelRows,
        embeddingModelRows,
        toolOptions,
        collectionOptions,
        memoryPolicyOptions,
    } = useAgentFormRefs();

    const runtimeKind = Form.useWatch("runtimeKind", form);
    const isReact = runtimeKind === AGENT_RUNTIME.REACT;

    async function onCreate(values: UpsertAgentFormValues) {
        setError(null);
        setCreatedId(null);
        setCreating(true);
        try {
            const kind = values.runtimeKind ?? AGENT_RUNTIME.REACT;
            const reactRuntime = kind === AGENT_RUNTIME.REACT;
            const id = await createAgent(buildUpsertAgentRequestBody(values, reactRuntime), {
                timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS,
            });
            setCreatedId(id);
            message.success("智能体创建成功");
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
                    icon={<RobotOutlined/>}
                    title="智能体"
                    subtitle="先选择 AgentScope 运行时形态，再填写模型与策略。与业界助手类产品一致：区分「推理+工具」与「纯对话」。"
                />

                <ErrorAlert error={error}/>

                <Alert
                    type="info"
                    showIcon
                    style={{marginBottom: 0}}
                    title="与 AgentScope 对齐"
                    description={
                        <span>
                            平台同步运行时使用 <Typography.Text code>io.agentscope.core.ReActAgent</Typography.Text>
                            ；「对话」类型为<strong>不挂载工具</strong>且 <Typography.Text
                            code>maxIters=1</Typography.Text>
                            的轻量配置。多轮人机、UserAgent、A2A 会话等需在对应网关/会话能力中接入，不在本页创建。
                        </span>
                    }
                />

                <SectionCard title="创建智能体">
                    <Form<UpsertAgentFormValues>
                        form={form}
                        layout="vertical"
                        onFinish={onCreate}
                        initialValues={{
                            runtimeKind: AGENT_RUNTIME.REACT,
                            maxReactIters: 10,
                            kbEnabled: false,
                            kbTopK: 5,
                            kbScoreThreshold: 0.25,
                            toolIds: [],
                        }}
                    >
                        <Typography.Title level={5} style={{marginTop: 0}}>
                            1. 运行时形态
                        </Typography.Title>
                        <Form.Item
                            name="runtimeKind"
                            rules={[{required: true, message: "请选择运行时"}]}
                        >
                            <RuntimeKindPicker/>
                        </Form.Item>

                        {isReact ? (
                            <Form.Item
                                name="maxReactIters"
                                label="ReAct 最大迭代步数（maxIters）"
                                extra="对应 ReActAgent.Builder.maxIters；过大可能增加耗时与费用，建议 4–20。"
                                rules={[
                                    {
                                        type: "number",
                                        min: 1,
                                        max: 64,
                                        message: "范围 1–64",
                                    },
                                ]}
                            >
                                <InputNumber min={1} max={64} style={{width: 200}}/>
                            </Form.Item>
                        ) : (
                            <Alert
                                type="warning"
                                showIcon
                                style={{marginBottom: 16}}
                                title="对话模式"
                                description="将不保存工具绑定；运行时 maxIters 固定为 1。知识库仍可按需开启。"
                            />
                        )}

                        <Typography.Title level={5}>2. 基本配置</Typography.Title>
                        <Form.Item
                            name="name"
                            label="名称"
                            rules={[{required: true, message: "请输入智能体名称"}]}
                        >
                            <Input placeholder="例如：订单助手"/>
                        </Form.Item>
                        <Form.Item
                            name="systemPrompt"
                            label="系统提示词"
                            rules={[{required: true, message: "请输入系统提示词"}]}
                        >
                            <Input.TextArea rows={6} placeholder="角色、边界、输出格式等"/>
                        </Form.Item>
                        <Form.Item
                            name="modelId"
                            label="默认聊天模型"
                            rules={[{required: true, message: "请选择模型配置"}]}
                        >
                            <Select
                                showSearch
                                allowClear={false}
                                loading={loadingRefs}
                                placeholder="搜索配置名称或模型"
                                options={toModelSelectOptions(chatModelRows)}
                                popupMatchSelectWidth={520}
                                filterOption={(input, option) =>
                                    filterModelSelectOption(input, option as { searchText?: string })
                                }
                            />
                        </Form.Item>

                        {isReact ? (
                            <>
                                <Divider plain/>
                                <Typography.Title level={5}>3. 工具能力</Typography.Title>
                                <Form.Item name="toolIds" label="可调用的工具">
                                    <Select
                                        mode="multiple"
                                        allowClear
                                        loading={loadingRefs}
                                        placeholder="选择已注册的工具（HTTP / MCP / 工作流等）"
                                        options={toolOptions}
                                        optionFilterProp="label"
                                        popupMatchSelectWidth={520}
                                        maxTagCount="responsive"
                                    />
                                </Form.Item>
                            </>
                        ) : null}

                        <Divider plain/>
                        <Typography.Title level={5}>{isReact ? "4" : "3"}. 记忆策略（可选）</Typography.Title>
                        <Typography.Paragraph type="secondary" style={{marginTop: 0}}>
                            在「记忆策略」页创建策略后在此绑定；运行时以 <Typography.Text
                            code>STATIC_CONTROL</Typography.Text>{" "}
                            挂载长期记忆，检索/写回参数由策略统一管理。
                        </Typography.Paragraph>
                        <Form.Item name="memoryPolicyId" label="记忆策略">
                            <Select
                                allowClear
                                showSearch
                                loading={loadingRefs}
                                placeholder="不启用则留空"
                                options={memoryPolicyOptions}
                                popupMatchSelectWidth={520}
                                filterOption={(input, option) =>
                                    filterMemoryPolicySelectOption(input, option as { searchText?: string })
                                }
                            />
                        </Form.Item>

                        <Divider plain/>
                        <Typography.Title level={5}>{isReact ? "5" : "4"}. 知识库 RAG（可选）</Typography.Title>
                        <Form.Item name="kbEnabled" label="启用知识库检索" valuePropName="checked">
                            <Switch/>
                        </Form.Item>
                        <Form.Item
                            noStyle
                            shouldUpdate={(prev, cur) => prev.kbEnabled !== cur.kbEnabled}
                        >
                            {({getFieldValue}) =>
                                getFieldValue("kbEnabled") ? (
                                    <>
                                        <Form.Item
                                            name="kbCollectionIds"
                                            label="知识集合"
                                            rules={[{required: true, message: "请至少选择一个集合"}]}
                                        >
                                            <Select
                                                mode="multiple"
                                                allowClear
                                                loading={loadingRefs}
                                                placeholder="选择一个或多个知识集合"
                                                options={collectionOptions}
                                                optionFilterProp="label"
                                            />
                                        </Form.Item>
                                        <Form.Item name="kbTopK" label="知识片段召回条数">
                                            <InputNumber min={1} max={50} style={{width: "100%"}}/>
                                        </Form.Item>
                                        <Form.Item name="kbScoreThreshold" label="知识相似度阈值">
                                            <InputNumber
                                                min={0}
                                                max={1}
                                                step={0.05}
                                                style={{width: "100%"}}
                                            />
                                        </Form.Item>
                                        <Form.Item
                                            name="kbEmbeddingModelId"
                                            label="嵌入模型覆盖（可选）"
                                            extra="一般不填，使用集合默认嵌入模型。"
                                        >
                                            <Select
                                                allowClear
                                                showSearch
                                                placeholder="选用 Embedding 模型配置"
                                                options={toModelSelectOptions(embeddingModelRows)}
                                                popupMatchSelectWidth={520}
                                                filterOption={(input, option) =>
                                                    filterModelSelectOption(input, option as { searchText?: string })
                                                }
                                            />
                                        </Form.Item>
                                    </>
                                ) : null
                            }
                        </Form.Item>

                        <Form.Item style={{marginTop: 16}}>
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
