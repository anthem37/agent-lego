"use client";

import {TeamOutlined} from "@ant-design/icons";
import {Button, Form, Input, message, Typography} from "antd";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {PageShell} from "@/components/PageShell";
import {SectionCard} from "@/components/SectionCard";
import {delegateA2a} from "@/lib/a2a/api";

type A2ADelegateForm = {
    agentId: string;
    modelId: string;
    input: string;
};

export default function A2APage() {
    const [calling, setCalling] = React.useState(false);
    const [output, setOutput] = React.useState<string | null>(null);
    const [error, setError] = React.useState<unknown>(null);
    const [form] = Form.useForm<A2ADelegateForm>();

    async function onDelegate(values: A2ADelegateForm) {
        setError(null);
        setOutput(null);
        setCalling(true);
        try {
            const out = await delegateA2a(values);
            setOutput(out);
            message.success("A2A 委派执行完成");
        } catch (e) {
            setError(e);
        } finally {
            setCalling(false);
        }
    }

    return (
        <AppLayout>
            <PageShell>
                <PageHeaderBlock
                    icon={<TeamOutlined/>}
                    title="A2A（本地委派调试）"
                    subtitle="把请求委派给本地 agent 执行，便于快速联调。"
                />

                <ErrorAlert error={error}/>

                <SectionCard title="delegate">
                    <Form<A2ADelegateForm> form={form} layout="vertical" onFinish={onDelegate}>
                        <Form.Item name="agentId" label="agentId" rules={[{required: true, message: "请输入 agentId"}]}>
                            <Input/>
                        </Form.Item>
                        <Form.Item name="modelId" label="modelId" rules={[{required: true, message: "请输入 modelId"}]}>
                            <Input/>
                        </Form.Item>
                        <Form.Item name="input" label="input" rules={[{required: true, message: "请输入 input"}]}>
                            <Input.TextArea rows={4}/>
                        </Form.Item>
                        <Form.Item>
                            <Button type="primary" htmlType="submit" loading={calling}>
                                委派执行
                            </Button>
                        </Form.Item>
                    </Form>

                    {output ? (
                        <pre style={{margin: 0, whiteSpace: "pre-wrap"}}>{output}</pre>
                    ) : (
                        <Typography.Text type="secondary">尚未执行</Typography.Text>
                    )}
                </SectionCard>
            </PageShell>
        </AppLayout>
    );
}

