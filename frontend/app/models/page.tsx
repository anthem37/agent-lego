"use client";

import {Button, Form, Input, message, Select, Space, Tag, Typography} from "antd";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {JsonTextArea} from "@/components/JsonTextArea";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {SectionCard} from "@/components/SectionCard";
import {request} from "@/lib/api/request";
import {parseJsonObject, stringifyPretty} from "@/lib/json";

type CreateModelRequest = {
    provider: string;
    modelKey: string;
    apiKey?: string;
    baseUrl?: string;
    configJson?: string;
};

type ProviderMeta = {
    provider: string;
    supportedConfigKeys: string[];
};

const FALLBACK_PROVIDERS: ProviderMeta[] = [
    {
        provider: "DASHSCOPE",
        supportedConfigKeys: ["temperature", "topP", "topK", "maxTokens", "seed", "additionalHeaders", "additionalBodyParams", "additionalQueryParams"]
    },
    {
        provider: "OPENAI",
        supportedConfigKeys: ["temperature", "topP", "maxTokens", "maxCompletionTokens", "seed", "endpointPath", "additionalHeaders", "additionalBodyParams", "additionalQueryParams"]
    },
    {
        provider: "ANTHROPIC",
        supportedConfigKeys: ["temperature", "topP", "maxTokens", "seed", "additionalHeaders", "additionalBodyParams", "additionalQueryParams"]
    },
];

export default function ModelsPage() {
    const [creating, setCreating] = React.useState(false);
    const [createdId, setCreatedId] = React.useState<string | null>(null);
    const [error, setError] = React.useState<unknown>(null);
    const [form] = Form.useForm<CreateModelRequest>();
    const [providers, setProviders] = React.useState<ProviderMeta[]>(FALLBACK_PROVIDERS);
    const providerValue = Form.useWatch("provider", form);
    const activeProvider = providers.find((p) => p.provider === (providerValue ?? "").trim().toUpperCase());

    React.useEffect(() => {
        let cancelled = false;
        void (async () => {
            try {
                const data = await request<ProviderMeta[]>("/models/providers");
                if (!cancelled && Array.isArray(data) && data.length > 0) {
                    setProviders(data);
                }
            } catch {
                // 失败时使用 fallback，不打扰用户
            }
        })();
        return () => {
            cancelled = true;
        };
    }, []);

    async function onCreate(values: CreateModelRequest) {
        setError(null);
        setCreatedId(null);
        setCreating(true);
        try {
            const config = values.configJson ? parseJsonObject(values.configJson) : undefined;
            const id = await request<string>("/models", {
                method: "POST",
                body: {
                    provider: values.provider,
                    modelKey: values.modelKey,
                    apiKey: values.apiKey,
                    baseUrl: values.baseUrl,
                    config,
                },
            });
            setCreatedId(id);
            message.success("模型创建成功");
        } catch (e) {
            setError(e);
        } finally {
            setCreating(false);
        }
    }

    return (
        <AppLayout>
            <Space orientation="vertical" size={16} style={{width: "100%"}}>
                <PageHeaderBlock title="模型" subtitle="创建模型配置，并在详情页触发一次 test。"/>

                <ErrorAlert error={error}/>

                <SectionCard title="创建模型">
                    <Form form={form} layout="vertical" onFinish={onCreate}>
                        <Form.Item
                            name="provider"
                            label="provider"
                            rules={[{required: true, message: "请输入 provider"}]}
                        >
                            <Select
                                showSearch
                                placeholder="请选择 provider（或输入）"
                                options={providers.map((p) => ({value: p.provider, label: p.provider}))}
                                filterOption={(input, option) =>
                                    (option?.value ?? "").toString().toLowerCase().includes(input.toLowerCase())
                                }
                            />
                        </Form.Item>
                        <Form.Item
                            name="modelKey"
                            label="modelKey"
                            rules={[{required: true, message: "请输入 modelKey"}]}
                        >
                            <Input placeholder="例如 qwen-plus"/>
                        </Form.Item>
                        <Form.Item name="apiKey" label="apiKey（可选）">
                            <Input.Password placeholder="仅用于本地联调，生产建议走更安全的密钥方案"/>
                        </Form.Item>
                        <Form.Item name="baseUrl" label="baseUrl（可选）">
                            <Input placeholder="例如 https://api.openai.com/v1 或私有网关地址"/>
                        </Form.Item>
                        {activeProvider ? (
                            <div style={{marginBottom: 12}}>
                                <Typography.Text type="secondary">
                                    支持的 config keys（提示）：{" "}
                                </Typography.Text>
                                <div style={{marginTop: 8, display: "flex", gap: 8, flexWrap: "wrap"}}>
                                    {activeProvider.supportedConfigKeys.map((k) => (
                                        <Tag key={k}>{k}</Tag>
                                    ))}
                                </div>
                            </div>
                        ) : null}
                        <Form.Item name="configJson" label="config（JSON，可选）">
                            <JsonTextArea
                                rows={10}
                                sample={{
                                    temperature: 0.7,
                                    maxTokens: 1024,
                                    topP: 0.9,
                                    additionalHeaders: {"x-trace": "demo"},
                                }}
                            />
                        </Form.Item>
                        <Typography.Paragraph type="secondary" style={{marginTop: -8}}>
                            合法性校验：后端会根据 provider 校验 config keys，避免提交无效参数。当前 provider 列表：{" "}
                            <Typography.Text code>{stringifyPretty(providers.map((p) => p.provider))}</Typography.Text>
                        </Typography.Paragraph>
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

