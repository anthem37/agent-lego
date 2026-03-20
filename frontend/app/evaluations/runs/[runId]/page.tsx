"use client";

import {Button, Card, Descriptions, Space, Typography} from "antd";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {request} from "@/lib/api/request";
import {stringifyPretty} from "@/lib/json";

type RunEvaluationDto = {
    id: string;
    evaluationId: string;
    status: string;
    input?: Record<string, unknown>;
    metrics?: Record<string, unknown>;
    trace?: Record<string, unknown>;
    error?: string;
    startedAt?: string;
    finishedAt?: string;
    createdAt?: string;
};

export default function EvaluationRunDetailPage(props: { params: Promise<{ runId: string }> }) {
    const [run, setRun] = React.useState<RunEvaluationDto | null>(null);
    const [loading, setLoading] = React.useState(false);
    const [error, setError] = React.useState<unknown>(null);

    async function reload(runId: string) {
        setError(null);
        setLoading(true);
        try {
            const data = await request<RunEvaluationDto>(`/evaluations/runs/${runId}`);
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
            <Space orientation="vertical" size={16} style={{width: "100%"}}>
                <div>
                    <Typography.Title level={3} style={{margin: 0}}>
                        评测运行详情
                    </Typography.Title>
                    <Typography.Text type="secondary">
                        {run ? (
                            <>
                                runId：<Typography.Text code>{run.id}</Typography.Text>（{run.status}）
                            </>
                        ) : (
                            "加载中…"
                        )}
                    </Typography.Text>
                </div>

                <ErrorAlert error={error}/>

                <Card
                    title="状态与结果"
                    extra={
                        <Space>
                            <Button
                                onClick={() => {
                                    void props.params.then(({runId}) => reload(runId));
                                }}
                                loading={loading}
                            >
                                刷新
                            </Button>
                        </Space>
                    }
                >
                    {run ? (
                        <Descriptions column={1} size="small">
                            <Descriptions.Item label="evaluationId">{run.evaluationId}</Descriptions.Item>
                            <Descriptions.Item label="status">{run.status}</Descriptions.Item>
                            <Descriptions.Item label="startedAt">{run.startedAt ?? "-"}</Descriptions.Item>
                            <Descriptions.Item label="finishedAt">{run.finishedAt ?? "-"}</Descriptions.Item>
                            <Descriptions.Item label="error">{run.error ?? "-"}</Descriptions.Item>
                            <Descriptions.Item label="metrics">
                                <pre style={{
                                    margin: 0,
                                    whiteSpace: "pre-wrap"
                                }}>{stringifyPretty(run.metrics ?? {})}</pre>
                            </Descriptions.Item>
                            <Descriptions.Item label="trace">
                                <pre
                                    style={{margin: 0, whiteSpace: "pre-wrap"}}>{stringifyPretty(run.trace ?? {})}</pre>
                            </Descriptions.Item>
                        </Descriptions>
                    ) : (
                        <Typography.Text type="secondary">未加载到数据</Typography.Text>
                    )}
                </Card>
            </Space>
        </AppLayout>
    );
}

