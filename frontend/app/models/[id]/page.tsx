"use client";

import {Button, Descriptions, Popconfirm, Space, Typography} from "antd";
import Link from "next/link";
import {useRouter} from "next/navigation";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {SectionCard} from "@/components/SectionCard";
import {ModelConfigDisplay} from "@/components/ModelConfigDisplay";
import {providerDisplayName} from "@/lib/model-config-labels";
import {request} from "@/lib/api/request";

type ModelDto = {
    id: string;
    name: string;
    description?: string;
    provider: string;
    modelKey: string;
    baseUrl?: string;
    config?: Record<string, unknown>;
    createdAt?: string;
    apiKeyConfigured?: boolean;
};

type TestModelResponse = {
    message: string;
    raw: string;
};

export default function ModelDetailPage(props: { params: Promise<{ id: string }> }) {
    const router = useRouter();
    const [modelId, setModelId] = React.useState<string | null>(null);
    const [model, setModel] = React.useState<ModelDto | null>(null);
    const [testResult, setTestResult] = React.useState<TestModelResponse | null>(null);
    const [loading, setLoading] = React.useState(false);
    const [testing, setTesting] = React.useState(false);
    const [deleting, setDeleting] = React.useState(false);
    const [error, setError] = React.useState<unknown>(null);

    React.useEffect(() => {
        let cancelled = false;
        void props.params.then(({id}) => {
            if (!cancelled) {
                setModelId(id);
            }
        });
        return () => {
            cancelled = true;
        };
    }, [props.params]);

    React.useEffect(() => {
        if (!modelId) {
            return;
        }
        let cancelled = false;
        setLoading(true);
        setError(null);
        void (async () => {
            try {
                const data = await request<ModelDto>(`/models/${modelId}`);
                if (!cancelled) {
                    setModel(data);
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
        })();
        return () => {
            cancelled = true;
        };
    }, [modelId]);

    const canConnectivityTest =
        model != null && model.provider.trim().toUpperCase() === "DASHSCOPE";

    async function onTest() {
        if (!model) {
            return;
        }
        setError(null);
        setTesting(true);
        try {
            const data = await request<TestModelResponse>(`/models/${model.id}/test`, {method: "POST"});
            setTestResult(data);
        } catch (e) {
            setError(e);
        } finally {
            setTesting(false);
        }
    }

    async function onDelete() {
        if (!model) {
            return;
        }
        setError(null);
        setDeleting(true);
        try {
            await request(`/models/${model.id}`, {method: "DELETE"});
            router.push("/models");
        } catch (e) {
            setError(e);
        } finally {
            setDeleting(false);
        }
    }

    return (
        <AppLayout>
            <Space orientation="vertical" size={16} style={{width: "100%"}}>
                <PageHeaderBlock
                    title="模型详情"
                    subtitle={model ? `${model.name} · ID：${model.id}` : "加载中…"}
                    extra={
                        <Space wrap>
                            <Link href="/models">
                                <Button>返回列表</Button>
                            </Link>
                            {model ? (
                                <>
                                    <Link href={`/models?edit=${model.id}`}>
                                        <Button type="primary">编辑</Button>
                                    </Link>
                                    <Popconfirm
                                        title="确认删除该模型？"
                                        description="若智能体仍绑定此模型，删除会被拒绝。"
                                        okText="删除"
                                        cancelText="取消"
                                        okButtonProps={{danger: true}}
                                        onConfirm={() => void onDelete()}
                                    >
                                        <Button danger loading={deleting}>
                                            删除
                                        </Button>
                                    </Popconfirm>
                                </>
                            ) : null}
                        </Space>
                    }
                />

                <ErrorAlert error={error}/>

                <SectionCard
                    title="基本信息"
                    extra={
                        model ? (
                            <Typography.Text type="secondary">
                                {providerDisplayName(model.provider)}
                            </Typography.Text>
                        ) : null
                    }
                >
                    {model ? (
                        <Descriptions column={1} size="small">
                            <Descriptions.Item label="配置名称">{model.name}</Descriptions.Item>
                            <Descriptions.Item
                                label="备注">{model.description?.trim() ? model.description : "—"}</Descriptions.Item>
                            <Descriptions.Item
                                label={
                                    <span>
                                        提供方
                                        <Typography.Text type="secondary" style={{marginLeft: 6, fontSize: 12}}>
                                            provider
                                        </Typography.Text>
                                    </span>
                                }
                            >
                                {providerDisplayName(model.provider)}
                                <Typography.Text type="secondary" style={{marginLeft: 8, fontSize: 12}}>
                                    （{model.provider}）
                                </Typography.Text>
                            </Descriptions.Item>
                            <Descriptions.Item
                                label={
                                    <span>
                                        模型标识
                                        <Typography.Text type="secondary" style={{marginLeft: 6, fontSize: 12}}>
                                            modelKey
                                        </Typography.Text>
                                    </span>
                                }
                            >
                                {model.modelKey}
                            </Descriptions.Item>
                            <Descriptions.Item
                                label={
                                    <span>
                                        接口根地址
                                        <Typography.Text type="secondary" style={{marginLeft: 6, fontSize: 12}}>
                                            baseUrl
                                        </Typography.Text>
                                    </span>
                                }
                            >
                                {model.baseUrl ?? "—"}
                            </Descriptions.Item>
                            <Descriptions.Item label="访问密钥（apiKey）">
                                {model.apiKeyConfigured ? "已配置（出于安全不在此展示内容）" : "未配置"}
                            </Descriptions.Item>
                            <Descriptions.Item label="创建时间">{model.createdAt ?? "—"}</Descriptions.Item>
                        </Descriptions>
                    ) : (
                        <Typography.Text type="secondary">{loading ? "加载中…" : "未加载到数据"}</Typography.Text>
                    )}
                </SectionCard>

                {model ? (
                    <SectionCard title="默认推理与请求参数">
                        <ModelConfigDisplay config={model.config}/>
                    </SectionCard>
                ) : null}

                <SectionCard title="连通性测试">
                    <Space orientation="vertical" size={12} style={{width: "100%"}}>
                        <Typography.Paragraph type="secondary" style={{marginBottom: 0}}>
                            向当前模型发起一次最小调用，用于验证密钥与网络是否可用。当前后端仅对{" "}
                            <Typography.Text code>DASHSCOPE</Typography.Text>{" "}
                            提供方实现连通性测试；其他提供方调用接口将返回 400。
                        </Typography.Paragraph>
                        <Button
                            type="primary"
                            onClick={() => void onTest()}
                            loading={testing}
                            disabled={!canConnectivityTest}
                        >
                            执行连通性测试
                        </Button>
                        {testResult ? (
                            <Descriptions column={1} size="small" bordered>
                                <Descriptions.Item label="返回消息">{testResult.message}</Descriptions.Item>
                                <Descriptions.Item label="原始内容（raw）">
                                    <pre style={{margin: 0, whiteSpace: "pre-wrap"}}>{testResult.raw}</pre>
                                </Descriptions.Item>
                            </Descriptions>
                        ) : (
                            <Typography.Text type="secondary">尚未执行</Typography.Text>
                        )}
                    </Space>
                </SectionCard>
            </Space>
        </AppLayout>
    );
}
