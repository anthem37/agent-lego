"use client";

import {ExperimentOutlined} from "@ant-design/icons";
import {Button, Descriptions, Typography} from "antd";
import React from "react";

import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {PageShell} from "@/components/PageShell";
import {SectionCard} from "@/components/SectionCard";
import {stringifyPretty} from "@/lib/json";
import {getEvaluationRun} from "@/lib/evaluations/api";
import type {RunEvaluationDto} from "@/lib/evaluations/types";

export default function EvaluationRunDetailPage(props: { params: Promise<{ runId: string }> }) {
    const [run, setRun] = React.useState<RunEvaluationDto | null>(null);
    const [loading, setLoading] = React.useState(false);
    const [error, setError] = React.useState<unknown>(null);

    async function reload(runId: string) {
        setError(null);
        setLoading(true);
        try {
            const data = await getEvaluationRun(runId);
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
                    icon={<ExperimentOutlined/>}
                    backHref="/evaluations"
                    title="评测运行详情"
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
                    title="状态与结果"
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
                </SectionCard>
            </PageShell>
        </AppLayout>
    );
}

