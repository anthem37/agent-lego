"use client";

import {Button, Form, Input, message, Select, Space, Typography} from "antd";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {JsonTextArea} from "@/components/JsonTextArea";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {SectionCard} from "@/components/SectionCard";
import {type ModelOptionRow, toModelSelectOptions} from "@/lib/model-select-options";
import {request} from "@/lib/api/request";
import {parseJsonObject} from "@/lib/json";

type CreateAgentForm = {
    name: string;
    systemPrompt: string;
    modelId: string;
    toolIdsText?: string;
    memoryPolicyJson?: string;
    knowledgeBasePolicyJson?: string;
};

export default function AgentsPage() {
    const [creating, setCreating] = React.useState(false);
    const [createdId, setCreatedId] = React.useState<string | null>(null);
    const [error, setError] = React.useState<unknown>(null);
    const [modelRows, setModelRows] = React.useState<ModelOptionRow[]>([]);
    const [form] = Form.useForm<CreateAgentForm>();

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

    async function onCreate(values: CreateAgentForm) {
        setError(null);
        setCreatedId(null);
        setCreating(true);
        try {
            const toolIds = values.toolIdsText
                ? values.toolIdsText
                    .split(",")
                    .map((s) => s.trim())
                    .filter(Boolean)
                : undefined;

            const memoryPolicy = values.memoryPolicyJson ? parseJsonObject(values.memoryPolicyJson) : undefined;

            const id = await request<string>("/agents", {
                method: "POST",
                body: {
                    name: values.name,
                    systemPrompt: values.systemPrompt,
                    modelId: values.modelId,
                    toolIds,
                    memoryPolicy,
                },
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
            <Space orientation="vertical" size={16} style={{width: "100%"}}>
                <PageHeaderBlock
                    title="智能体"
                    subtitle="绑定对象须为聊天类模型配置（非文本嵌入）。下拉里已隐藏嵌入类配置。"
                />

                <ErrorAlert error={error}/>

                <SectionCard title="创建智能体">
                    <Form form={form} layout="vertical" onFinish={onCreate}>
                        <Form.Item name="name" label="name" rules={[{required: true, message: "请输入 name"}]}>
                            <Input placeholder="例如 订单助手"/>
                        </Form.Item>
                        <Form.Item
                            name="systemPrompt"
                            label="systemPrompt"
                            rules={[{required: true, message: "请输入 systemPrompt"}]}
                        >
                            <Input.TextArea rows={6} placeholder="写清楚角色、边界、输出格式等"/>
                        </Form.Item>
                        <Form.Item
                            name="modelId"
                            label="默认绑定的模型配置"
                            rules={[{required: true, message: "请选择一条模型配置"}]}
                        >
                            <Select
                                showSearch
                                allowClear={false}
                                placeholder="搜索配置名称、模型标识或编号"
                                options={toModelSelectOptions(chatModelRows)}
                                popupMatchSelectWidth={520}
                                filterOption={(input, option) => {
                                    const st = (option as { searchText?: string }).searchText ?? "";
                                    const q = input.trim().toLowerCase();
                                    return !q || st.includes(q);
                                }}
                            />
                        </Form.Item>
                        <Form.Item name="toolIdsText" label="toolIds（逗号分隔，可选）">
                            <Input placeholder="例如 123,456"/>
                        </Form.Item>
                        <Form.Item name="memoryPolicyJson" label="memoryPolicy（JSON，可选）">
                            <JsonTextArea rows={8} sample={{ownerScope: "demo", topK: 5}}/>
                        </Form.Item>
                        <Form.Item name="knowledgeBasePolicyJson" label="knowledgeBasePolicy（JSON，可选）">
                            <JsonTextArea
                                rows={8}
                                sample={{collectionIds: ["集合ID"], topK: 5, scoreThreshold: 0.25}}
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

