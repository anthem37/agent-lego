"use client";

import {CloudServerOutlined} from "@ant-design/icons";
import {Alert, Button, Descriptions, Input, InputNumber, Popconfirm, Space, Tag, Typography} from "antd";
import Link from "next/link";
import {useRouter} from "next/navigation";
import React from "react";

import {isAbortError} from "@/lib/api/isAbortError";
import {DEFAULT_REQUEST_TIMEOUT_MS} from "@/lib/api/request";
import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {PageShell} from "@/components/PageShell";
import {SectionCard} from "@/components/SectionCard";
import {ModelConfigDisplay} from "@/components/ModelConfigDisplay";
import {providerDisplayName} from "@/lib/model-config-labels";
import {deleteModel, getModel, testModel} from "@/lib/models/api";
import type {ModelDto, TestModelResponse} from "@/lib/models/types";

export default function ModelDetailPage(props: { params: Promise<{ id: string }> }) {
    const router = useRouter();
    const [modelId, setModelId] = React.useState<string | null>(null);
    const [model, setModel] = React.useState<ModelDto | null>(null);
    const [testResult, setTestResult] = React.useState<TestModelResponse | null>(null);
    const [loading, setLoading] = React.useState(false);
    const [testing, setTesting] = React.useState(false);
    const [deleting, setDeleting] = React.useState(false);
    const [error, setError] = React.useState<unknown>(null);
    const [testPrompt, setTestPrompt] = React.useState("");
    const [testMaxTokens, setTestMaxTokens] = React.useState<number | null>(null);
    const [testMaxStreamChunks, setTestMaxStreamChunks] = React.useState<number | null>(null);

    const testAbortRef = React.useRef<AbortController | null>(null);
    React.useEffect(() => {
        return () => {
            testAbortRef.current?.abort();
        };
    }, []);

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
        const ac = new AbortController();
        setLoading(true);
        setError(null);
        void (async () => {
            try {
                const data = await getModel(modelId, {
                    signal: ac.signal,
                    timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS,
                });
                setModel(data);
            } catch (e) {
                if (!isAbortError(e)) {
                    setError(e);
                }
            } finally {
                if (!ac.signal.aborted) {
                    setLoading(false);
                }
            }
        })();
        return () => {
            ac.abort();
        };
    }, [modelId]);

    const isChatProvider =
        model != null && new Set(["DASHSCOPE", "OPENAI", "ANTHROPIC"]).has(model.provider.trim().toUpperCase());
    const isEmbeddingProvider = model != null && model.provider.toUpperCase().includes("EMBEDDING");
    const canRunModelTest = isChatProvider || isEmbeddingProvider;

    async function onTest() {
        if (!model) {
            return;
        }
        setError(null);
        setTesting(true);
        testAbortRef.current?.abort();
        const runAc = new AbortController();
        testAbortRef.current = runAc;
        try {
            const body: Record<string, unknown> = {};
            if (testPrompt.trim()) {
                body.prompt = testPrompt.trim();
            }
            if (testMaxTokens != null && testMaxTokens > 0) {
                body.maxTokens = testMaxTokens;
            }
            if (testMaxStreamChunks != null && testMaxStreamChunks > 0) {
                body.maxStreamChunks = testMaxStreamChunks;
            }
            const data = await testModel(model.id, body, {signal: runAc.signal});
            setTestResult(data);
        } catch (e) {
            if (!isAbortError(e)) {
                setError(e);
            }
        } finally {
            const stillThisRun = testAbortRef.current === runAc;
            if (stillThisRun) {
                testAbortRef.current = null;
                setTesting(false);
            }
        }
    }

    async function onDelete() {
        if (!model) {
            return;
        }
        setError(null);
        setDeleting(true);
        try {
            await deleteModel(model.id, {timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS});
            router.push("/models");
        } catch (e) {
            setError(e);
        } finally {
            setDeleting(false);
        }
    }

    return (
        <AppLayout>
            <PageShell>
                <PageHeaderBlock
                    icon={<CloudServerOutlined/>}
                    backHref="/models"
                    title="模型详情"
                    subtitle={model ? `${model.name} · ID：${model.id}` : "加载中…"}
                    extra={
                        <Space wrap>
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

                {model ? (
                    <Alert
                        type="info"
                        showIcon
                        style={{marginBottom: 0}}
                        title="与其他模块的联动"
                        description={
                            <Space orientation="vertical" size={4}>
                                <span>
                                    <Link href="/agents">创建或编辑智能体</Link>
                                    时可绑定本模型；嵌入类模型用于
                                    <Link href="/kb"> 知识库 </Link>的向量检索与入库。
                                </span>
                                {isChatProvider ? (
                                    <span>对话类模型用于智能体主对话与工具调用后的回复生成。</span>
                                ) : null}
                                {isEmbeddingProvider ? (
                                    <span>嵌入模型请与知识库写入时选用的一致，否则维度不匹配无法检索。</span>
                                ) : null}
                            </Space>
                        }
                    />
                ) : null}

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

                <SectionCard title="模型测试（连通性 / 能力探测）">
                    <Space orientation="vertical" size={12} style={{width: "100%"}}>
                        <Typography.Paragraph type="secondary" style={{marginBottom: 0}}>
                            <b>聊天模型</b>：按当前配置走流式生成接口，默认采集多条 chunk（可限制条数），并统计耗时。
                            留空则使用服务端默认提示词与 maxTokens（见 <Typography.Text
                            code>application.yml</Typography.Text>{" "}
                            <Typography.Text code>model.connectivity</Typography.Text>）。
                        </Typography.Paragraph>
                        <Typography.Paragraph type="secondary" style={{marginBottom: 0}}>
                            <b>Embedding 模型</b>：对下方文本做一次真实 embed，返回维度与向量前几维预览；失败时返回{" "}
                            <Typography.Text code>status=ERROR</Typography.Text>（HTTP 仍为 200，便于在页面查看原因）。
                        </Typography.Paragraph>
                        {!canRunModelTest && model ? (
                            <Alert type="warning" showIcon title="当前提供方不支持在线测试"/>
                        ) : null}
                        <div>
                            <Typography.Text strong>探测文本（prompt）</Typography.Text>
                            <Typography.Paragraph type="secondary" style={{marginBottom: 6, marginTop: 4}}>
                                聊天：作为 user 消息；Embedding：作为待向量化的输入。最长 8000 字符。
                            </Typography.Paragraph>
                            <Input.TextArea
                                rows={3}
                                placeholder={
                                    isEmbeddingProvider
                                        ? "留空则使用默认句子：AgentLego embedding connectivity test"
                                        : "留空则使用服务端默认英文短提示（如 Reply with a single word: OK.）"
                                }
                                value={testPrompt}
                                onChange={(e) => setTestPrompt(e.target.value)}
                                maxLength={8000}
                                showCount
                            />
                        </div>
                        {isChatProvider ? (
                            <Space wrap size="middle">
                                <div>
                                    <Typography.Text strong>maxTokens</Typography.Text>
                                    <Typography.Paragraph type="secondary" style={{marginBottom: 4, marginTop: 2}}>
                                        覆盖本次测试；留空用服务端默认
                                    </Typography.Paragraph>
                                    <InputNumber
                                        min={1}
                                        max={8192}
                                        placeholder="默认"
                                        value={testMaxTokens ?? undefined}
                                        onChange={(v) => setTestMaxTokens(v === null ? null : Number(v))}
                                    />
                                </div>
                                <div>
                                    <Typography.Text strong>maxStreamChunks</Typography.Text>
                                    <Typography.Paragraph type="secondary" style={{marginBottom: 4, marginTop: 2}}>
                                        最多采集流式响应条数（1–128）
                                    </Typography.Paragraph>
                                    <InputNumber
                                        min={1}
                                        max={128}
                                        placeholder="默认"
                                        value={testMaxStreamChunks ?? undefined}
                                        onChange={(v) => setTestMaxStreamChunks(v === null ? null : Number(v))}
                                    />
                                </div>
                            </Space>
                        ) : null}
                        <Button
                            type="primary"
                            onClick={() => void onTest()}
                            loading={testing}
                            disabled={!canRunModelTest}
                        >
                            执行测试
                        </Button>
                        {testResult ? (
                            <Space orientation="vertical" size={12} style={{width: "100%"}}>
                                {testResult.status === "ERROR" ? (
                                    <Alert type="error" showIcon title={testResult.message}/>
                                ) : testResult.status === "EMPTY" ? (
                                    <Alert type="warning" showIcon title="模型返回空文本（EMPTY）"/>
                                ) : (
                                    <Alert
                                        type="success"
                                        showIcon
                                        title={testResult.message ?? "测试成功"}
                                    />
                                )}
                                <Descriptions column={1} size="small" bordered>
                                    {testResult.testType ? (
                                        <Descriptions.Item label="类型">
                                            <Tag>{testResult.testType}</Tag>
                                        </Descriptions.Item>
                                    ) : null}
                                    {testResult.status ? (
                                        <Descriptions.Item label="状态">
                                            <Tag
                                                color={
                                                    testResult.status === "OK"
                                                        ? "green"
                                                        : testResult.status === "ERROR"
                                                            ? "red"
                                                            : "orange"
                                                }
                                            >
                                                {testResult.status}
                                            </Tag>
                                        </Descriptions.Item>
                                    ) : null}
                                    {testResult.latencyMs != null ? (
                                        <Descriptions.Item label="耗时（ms）">{testResult.latencyMs}</Descriptions.Item>
                                    ) : null}
                                    {testResult.streamChunks != null ? (
                                        <Descriptions.Item
                                            label="流式 chunk 数">{testResult.streamChunks}</Descriptions.Item>
                                    ) : null}
                                    {testResult.maxTokensUsed != null ? (
                                        <Descriptions.Item
                                            label="本次 maxTokens">{testResult.maxTokensUsed}</Descriptions.Item>
                                    ) : null}
                                    {testResult.promptUsed ? (
                                        <Descriptions.Item label="实际发送的 prompt">
                                            <pre style={{margin: 0, whiteSpace: "pre-wrap", fontSize: 12}}>
                                                {testResult.promptUsed}
                                            </pre>
                                        </Descriptions.Item>
                                    ) : null}
                                    {testResult.embeddingDimension != null ? (
                                        <Descriptions.Item label="向量维度">
                                            {testResult.embeddingDimension}
                                        </Descriptions.Item>
                                    ) : null}
                                    {testResult.embeddingPreview ? (
                                        <Descriptions.Item label="向量预览">
                                            <Typography.Text code copyable>
                                                {testResult.embeddingPreview}
                                            </Typography.Text>
                                        </Descriptions.Item>
                                    ) : null}
                                    {testResult.testType === "CHAT" && testResult.status === "OK" ? (
                                        <Descriptions.Item label="模型输出">
                                            <pre style={{margin: 0, whiteSpace: "pre-wrap"}}>
                                                {testResult.raw ?? testResult.message}
                                            </pre>
                                        </Descriptions.Item>
                                    ) : (
                                        <>
                                            {testResult.message ? (
                                                <Descriptions.Item label="摘要">
                                                    {testResult.message}
                                                </Descriptions.Item>
                                            ) : null}
                                            {testResult.raw != null && testResult.raw !== "" ? (
                                                <Descriptions.Item label="详情 / raw">
                                                    <pre style={{margin: 0, whiteSpace: "pre-wrap"}}>
                                                        {testResult.raw}
                                                    </pre>
                                                </Descriptions.Item>
                                            ) : null}
                                        </>
                                    )}
                                </Descriptions>
                            </Space>
                        ) : (
                            <Typography.Text type="secondary">尚未执行</Typography.Text>
                        )}
                    </Space>
                </SectionCard>
            </PageShell>
        </AppLayout>
    );
}
