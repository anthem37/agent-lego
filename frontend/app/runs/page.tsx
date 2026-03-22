"use client";

import {PlayCircleOutlined} from "@ant-design/icons";
import {Button, Form, Input} from "antd";
import {useRouter} from "next/navigation";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {PageShell} from "@/components/PageShell";
import {SectionCard} from "@/components/SectionCard";

type RunQueryForm = {
    runId: string;
};

export default function RunsIndexPage() {
    const router = useRouter();
    const [form] = Form.useForm<RunQueryForm>();

    function onGo(values: RunQueryForm) {
        router.push(`/runs/${values.runId}`);
    }

    return (
        <AppLayout>
            <PageShell>
                <PageHeaderBlock
                    icon={<PlayCircleOutlined/>}
                    title="运行查询"
                    subtitle="由工作流触发运行后得到 runId；在此输入 runId 查看状态、输入输出与错误信息。"
                />

                <SectionCard title="按 runId 查询">
                    <Form form={form} layout="vertical" onFinish={onGo}>
                        <Form.Item name="runId" label="runId" rules={[{required: true, message: "请输入 runId"}]}>
                            <Input placeholder="从 /workflows/{id}/runs 返回的 runId"/>
                        </Form.Item>
                        <Form.Item>
                            <Button type="primary" htmlType="submit">
                                查询
                            </Button>
                        </Form.Item>
                    </Form>
                </SectionCard>
            </PageShell>
        </AppLayout>
    );
}
