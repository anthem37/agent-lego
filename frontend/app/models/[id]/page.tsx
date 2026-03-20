"use client";

import {Button, Card, Descriptions, Space, Typography} from "antd";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {request} from "@/lib/api/request";

type ModelDto = {
    id: string;
    provider: string;
    modelKey: string;
    baseUrl?: string;
    config?: Record<string, unknown>;
    createdAt?: string;
};

type TestModelResponse = {
    message: string;
    raw: string;
};

export default function ModelDetailPage(props: { params: Promise<{ id: string }> }) {
    const [model, setModel] = React.useState<ModelDto | null>(null);
    const [testResult, setTestResult] = React.useState<TestModelResponse | null>(null);
    const [loading, setLoading] = React.useState(false);
    const [testing, setTesting] = React.useState(false);
    const [error, setError] = React.useState<unknown>(null);

    React.useEffect(() => {
        let cancelled = false;
        setLoading(true);
        setError(null);
        void props.params.then(async ({id}) => {
            try {
                const data = await request<ModelDto>(`/models/${id}`);
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
        });
        return () => {
            cancelled = true;
        };
    }, [props.params]);

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

    return (
        <AppLayout>
            <Space orientation="vertical" size={16} style={{width: "100%"}}>
                <div>
                    <Typography.Title level={3} style={{margin: 0}}>
                        模型详情
                    </Typography.Title>
                    <Typography.Text type="secondary">
                        {model ? (
                            <>
                                ID：<Typography.Text code>{model.id}</Typography.Text>
                            </>
                        ) : (
                            "加载中…"
                        )}
                    </Typography.Text>
                </div>

                <ErrorAlert error={error}/>

                <Card title="基本信息" loading={loading}>
                    {model ? (
                        <Descriptions column={1} size="small">
                            <Descriptions.Item label="provider">{model.provider}</Descriptions.Item>
                            <Descriptions.Item label="modelKey">{model.modelKey}</Descriptions.Item>
                            <Descriptions.Item label="baseUrl">{model.baseUrl ?? "-"}</Descriptions.Item>
                            <Descriptions.Item label="createdAt">{model.createdAt ?? "-"}</Descriptions.Item>
                            <Descriptions.Item label="config">
                <pre style={{margin: 0, whiteSpace: "pre-wrap"}}>
                  {model.config ? JSON.stringify(model.config, null, 2) : "{}"}
                </pre>
                            </Descriptions.Item>
                        </Descriptions>
                    ) : (
                        <Typography.Text type="secondary">未加载到数据</Typography.Text>
                    )}
                </Card>

                <Card title="连通性测试">
                    <Space orientation="vertical" size={12} style={{width: "100%"}}>
                        <Button type="primary" onClick={onTest} loading={testing} disabled={!model}>
                            test
                        </Button>
                        {testResult ? (
                            <pre style={{margin: 0, whiteSpace: "pre-wrap"}}>
                {JSON.stringify(testResult, null, 2)}
              </pre>
                        ) : (
                            <Typography.Text type="secondary">尚未执行</Typography.Text>
                        )}
                    </Space>
                </Card>
            </Space>
        </AppLayout>
    );
}

