"use client";

import {PlayCircleOutlined} from "@ant-design/icons";
import {Button, Descriptions, Typography} from "antd";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {PageShell} from "@/components/PageShell";
import {SectionCard} from "@/components/SectionCard";
import {stringifyPretty} from "@/lib/json";
import {getWorkflowRun} from "@/lib/runs/api";
import type {WorkflowRunDto} from "@/lib/runs/types";

export default function RunDetailPage(props: { params: Promise<{ runId: string }> }) {
    const [run, setRun] = React.useState<WorkflowRunDto | null>(null);
    const [loading, setLoading] = React.useState(false);
    const [error, setError] = React.useState<unknown>(null);

    async function reload(runId: string) {
        setError(null);
        setLoading(true);
        try {
            const data = await getWorkflowRun(runId);
            setRun(data);
        } catch (e) {
            setError(e);
        } finally {
            setLoading(false);
        }
    }

    React.useEffect(() => {
        let timer: number | null = null;
        let cancelled = false;

        void props.params.then(({runId}) => {
            void reload(runId);

            timer = window.setInterval(() => {
                if (!cancelled) {
                    void reload(runId);
                }
            }, 1500);
        });

        return () => {
            cancelled = true;
            if (timer) {
                window.clearInterval(timer);
            }
        };
    }, [props.params]);

    return (
        <AppLayout>
            <PageShell>
                <PageHeaderBlock
                    icon={<PlayCircleOutlined/>}
                    backHref="/runs"
                    title="工作流运行详情"
                    subtitle={
                        run ? (
                            <>
                                runId：<Typography.Text code>{run.id}</Typography.Text>（{run.status}）
                            </>
                        ) : (
                            "加载中…"
                        )
                    }
                />

                <ErrorAlert error={error}/>

                <SectionCard
                    title="运行状态"
                    extra={
                        <Button
                            onClick={() => {
                                void props.params.then(({runId}) => reload(runId));
                            }}
                            loading={loading}
                        >
                            刷新
                        </Button>
                    }
                >
                    {run ? (
                        <Descriptions column={1} size="small">
                            <Descriptions.Item label="workflowId">{run.workflowId}</Descriptions.Item>
                            <Descriptions.Item label="status">{run.status}</Descriptions.Item>
                            <Descriptions.Item label="startedAt">{run.startedAt ?? "-"}</Descriptions.Item>
                            <Descriptions.Item label="finishedAt">{run.finishedAt ?? "-"}</Descriptions.Item>
                            <Descriptions.Item label="error">{run.error ?? "-"}</Descriptions.Item>
                            <Descriptions.Item label="input">
                                <pre
                                    style={{margin: 0, whiteSpace: "pre-wrap"}}>{stringifyPretty(run.input ?? {})}</pre>
                            </Descriptions.Item>
                            <Descriptions.Item label="output">
                                <pre style={{
                                    margin: 0,
                                    whiteSpace: "pre-wrap"
                                }}>{stringifyPretty(run.output ?? {})}</pre>
                            </Descriptions.Item>
                        </Descriptions>
                    ) : (
                        <Typography.Text type="secondary">未加载到数据</Typography.Text>
                    )}
                </SectionCard>
            </PageShell>
        </AppLayout>
    );
}

