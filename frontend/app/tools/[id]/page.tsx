"use client";

import {Button, Descriptions, Form, message, Space, Spin, Typography} from "antd";
import Link from "next/link";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {JsonTextArea} from "@/components/JsonTextArea";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {SectionCard} from "@/components/SectionCard";
import {request} from "@/lib/api/request";
import {parseJsonObject, stringifyPretty} from "@/lib/json";
import {toolTypeDisplayName} from "@/lib/tool-labels";

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
            const input = values.inputJson?.trim() ? parseJsonObject(values.inputJson.trim()) : {};
            const data = await request<TestToolCallResponse>(`/tools/${tool.id}/test-call`, {
                method: "POST",
                body: {input},
            });
            setTestOut(data);
            message.success("调用完成");
        } catch (e) {
            setError(e);
        } finally {
            setTesting(false);
        }
    }

    return (
        <AppLayout>
            <Space orientation="vertical" size={16} style={{width: "100%"}}>
                <PageHeaderBlock
                    title={tool ? `工具：${tool.name}` : "工具详情"}
                    subtitle={tool ? `编号 ${tool.id}` : "加载中…"}
                    extra={
                        <Link href="/tools">
                            <Button>返回列表</Button>
                        </Link>
                    }
                />

                <ErrorAlert error={error}/>

                <SectionCard title="基本信息">
                    <Spin spinning={loading}>
                    {tool ? (
                        <Descriptions column={1} size="small" bordered>
                            <Descriptions.Item label="工具名称">{tool.name}</Descriptions.Item>
                            <Descriptions.Item label="类型">
                                {toolTypeDisplayName(tool.toolType)}
                                <Typography.Text type="secondary" style={{marginLeft: 8, fontSize: 12}}>
                                    {tool.toolType}
                                </Typography.Text>
                            </Descriptions.Item>
                            <Descriptions.Item label="创建时间">{tool.createdAt ?? "—"}</Descriptions.Item>
                            <Descriptions.Item label="编号（可复制给智能体 toolIds）">
                                <Typography.Text code copyable>
                                    {tool.id}
                                </Typography.Text>
                            </Descriptions.Item>
                            <Descriptions.Item label="定义（definition）">
                                <pre style={{margin: 0, whiteSpace: "pre-wrap", fontSize: 13}}>
                                    {stringifyPretty(tool.definition ?? {})}
                                </pre>
                            </Descriptions.Item>
                        </Descriptions>
                    ) : (
                        <Typography.Text type="secondary">{loading ? "加载中…" : "未加载到数据"}</Typography.Text>
                    )}
                    </Spin>
                </SectionCard>

                <SectionCard title="联调：test-call">
                    <Space orientation="vertical" size={12} style={{width: "100%"}}>
                        <Typography.Paragraph type="secondary" style={{marginBottom: 0}}>
                            向该工具传入 JSON 入参（可为空对象），查看返回结果。本地工具通常使用字段{" "}
                            <Typography.Text code>content</Typography.Text>。
                        </Typography.Paragraph>
                        <Form<TestToolCallForm> form={form} layout="vertical" onFinish={onTest}>
                            <Form.Item name="inputJson" label="调用入参（JSON，可选）">
                                <JsonTextArea rows={8} sample={{content: "hello"}}/>
                            </Form.Item>
                            <Form.Item>
                                <Button type="primary" htmlType="submit" loading={testing} disabled={!tool}>
                                    发起调用
                                </Button>
                            </Form.Item>
                        </Form>

                        {testOut ? (
                            <Descriptions column={1} size="small" bordered title="返回结果">
                                <Descriptions.Item label="输出（output）">
                                    <pre style={{margin: 0, whiteSpace: "pre-wrap"}}>
                                        {testOut.output ?? "—"}
                                    </pre>
                                </Descriptions.Item>
                                <Descriptions.Item label="原始（raw）">
                                    <pre style={{margin: 0, whiteSpace: "pre-wrap"}}>
                                        {testOut.raw ?? "—"}
                                    </pre>
                                </Descriptions.Item>
                            </Descriptions>
                        ) : (
                            <Typography.Text type="secondary">尚未执行调用</Typography.Text>
                        )}
                    </Space>
                </SectionCard>
            </Space>
        </AppLayout>
    );
}
