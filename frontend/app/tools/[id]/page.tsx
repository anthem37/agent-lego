"use client";

import {Button, Card, Descriptions, Form, Space, Typography} from "antd";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {JsonTextArea} from "@/components/JsonTextArea";
import {request} from "@/lib/api/request";
import {parseJsonObject, stringifyPretty} from "@/lib/json";

type ToolDto = {
    id: string;
    toolType: string;
    name: string;
    definition?: Record<string, unknown>;
    createdAt?: string;
};

type TestToolCallForm = {
    inputJson?: string;
};

type TestToolCallResponse = {
    output?: string;
    raw?: string;
};

export default function ToolDetailPage(props: { params: Promise<{ id: string }> }) {
    const [tool, setTool] = React.useState<ToolDto | null>(null);
    const [loading, setLoading] = React.useState(false);
    const [testing, setTesting] = React.useState(false);
    const [testOut, setTestOut] = React.useState<TestToolCallResponse | null>(null);
    const [error, setError] = React.useState<unknown>(null);
    const [form] = Form.useForm<TestToolCallForm>();

    React.useEffect(() => {
        let cancelled = false;
        setLoading(true);
        setError(null);
        void props.params.then(async ({id}) => {
            try {
                const data = await request<ToolDto>(`/tools/${id}`);
                if (!cancelled) {
                    setTool(data);
                    form.setFieldsValue({inputJson: ""});
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

    async function onTest(values: TestToolCallForm) {
        if (!tool) {
            return;
        }
        setError(null);
        setTesting(true);
        setTestOut(null);
        try {
            const input = values.inputJson ? parseJsonObject(values.inputJson) : {};
            const data = await request<TestToolCallResponse>(`/tools/${tool.id}/test-call`, {
                method: "POST",
                body: {input},
            });
            setTestOut(data);
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
                        工具详情
                    </Typography.Title>
                    <Typography.Text type="secondary">
                        {tool ? (
                            <>
                                ID：<Typography.Text code>{tool.id}</Typography.Text>
                            </>
                        ) : (
                            "加载中…"
                        )}
                    </Typography.Text>
                </div>

                <ErrorAlert error={error}/>

                <Card title="基本信息" loading={loading}>
                    {tool ? (
                        <Descriptions column={1} size="small">
                            <Descriptions.Item label="name">{tool.name}</Descriptions.Item>
                            <Descriptions.Item label="toolType">{tool.toolType}</Descriptions.Item>
                            <Descriptions.Item label="createdAt">{tool.createdAt ?? "-"}</Descriptions.Item>
                            <Descriptions.Item label="definition">
                                <pre style={{
                                    margin: 0,
                                    whiteSpace: "pre-wrap"
                                }}>{stringifyPretty(tool.definition ?? {})}</pre>
                            </Descriptions.Item>
                        </Descriptions>
                    ) : (
                        <Typography.Text type="secondary">未加载到数据</Typography.Text>
                    )}
                </Card>

                <Card title="test-call">
                    <Form<TestToolCallForm> form={form} layout="vertical" onFinish={onTest}>
                        <Form.Item name="inputJson" label="input（JSON）">
                            <JsonTextArea rows={8} sample={{content: "hello"}}/>
                        </Form.Item>
                        <Form.Item>
                            <Button type="primary" htmlType="submit" loading={testing} disabled={!tool}>
                                发起 test-call
                            </Button>
                        </Form.Item>
                    </Form>

                    {testOut ? (
                        <pre style={{margin: 0, whiteSpace: "pre-wrap"}}>{JSON.stringify(testOut, null, 2)}</pre>
                    ) : (
                        <Typography.Text type="secondary">尚未执行</Typography.Text>
                    )}
                </Card>
            </Space>
        </AppLayout>
    );
}

