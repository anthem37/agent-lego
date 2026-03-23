"use client";

import {RobotOutlined} from "@ant-design/icons";
import {
    Alert,
    Button,
    Col,
    Descriptions,
    Divider,
    Form,
    Input,
    InputNumber,
    message,
    Row,
    Select,
    Space,
    Switch,
    Tag,
    Typography,
} from "antd";
import Link from "next/link";
import React from "react";

import {isAbortError} from "@/lib/api/isAbortError";
import {DEFAULT_REQUEST_TIMEOUT_MS} from "@/lib/api/request";
import {AppLayout} from "@/components/AppLayout";
import {ErrorAlert} from "@/components/ErrorAlert";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {PageShell} from "@/components/PageShell";
import {SectionCard} from "@/components/SectionCard";
import {getAgent, runAgent, updateAgent} from "@/lib/agents/api";
import {buildRunOptions, buildUpsertAgentRequestBody, parseKbPolicyFromAgent} from "@/lib/agents/build-policy";
import {filterMemoryPolicySelectOption, filterModelSelectOption,} from "@/lib/agents/form-options";
import {runtimeKindLabel} from "@/lib/agents/runtime-labels";
import {AGENT_RUNTIME, AGENT_RUNTIME_OPTIONS} from "@/lib/agents/runtime-kinds";
import type {AgentDto, RunAgentForm, RunAgentResponse, UpsertAgentFormValues} from "@/lib/agents/types";
import {toModelSelectOptions} from "@/lib/model-select-options";
import {useAgentFormRefs} from "@/hooks/useAgentFormRefs";
import {stringifyPretty} from "@/lib/json";

export default function AgentDetailPage(props: { params: Promise<{ id: string }> }) {
    const [agent, setAgent] = React.useState<AgentDto | null>(null);
    const [loading, setLoading] = React.useState(false);
    const [savingEdit, setSavingEdit] = React.useState(false);
    const [running, setRunning] = React.useState(false);
    const [runOut, setRunOut] = React.useState<RunAgentResponse | null>(null);
    const [error, setError] = React.useState<unknown>(null);
    const [form] = Form.useForm<RunAgentForm>();
    const [editForm] = Form.useForm<UpsertAgentFormValues>();

    const {
        loadingRefs,
        chatModelRows,
        embeddingModelRows,
        toolOptions,
        collectionOptions,
        memoryPolicyOptions,
    } = useAgentFormRefs();

    const editRuntime = Form.useWatch("runtimeKind", editForm);
    const editIsReact = editRuntime === AGENT_RUNTIME.REACT;

    const runAbortRef = React.useRef<AbortController | null>(null);
    React.useEffect(() => {
        return () => {
            runAbortRef.current?.abort();
        };
    }, []);

    React.useEffect(() => {
        const ac = new AbortController();
        setLoading(true);
        setError(null);
        void props.params.then(async ({id}) => {
            try {
                const data = await getAgent(id, {
                    signal: ac.signal,
                    timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS,
                });
                setAgent(data);
                form.setFieldsValue({modelId: data.modelId});
            } catch (e) {
                if (!isAbortError(e)) {
                    setError(e);
                }
            } finally {
                if (!ac.signal.aborted) {
                    setLoading(false);
                }
            }
        });
        return () => {
            ac.abort();
        };
    }, [form, props.params]);

    React.useEffect(() => {
        if (!agent) {
            return;
        }
        const kb = parseKbPolicyFromAgent(agent.knowledgeBasePolicy);
        editForm.setFieldsValue({
            runtimeKind: (agent.runtimeKind as UpsertAgentFormValues["runtimeKind"]) ?? AGENT_RUNTIME.REACT,
            maxReactIters: agent.maxReactIters ?? 10,
            name: agent.name,
            systemPrompt: agent.systemPrompt,
            modelId: agent.modelId,
            toolIds: agent.toolIds ?? [],
            memoryPolicyId: agent.memoryPolicyId,
            ...kb,
        });
    }, [agent, editForm]);

    async function onSaveEdit(values: UpsertAgentFormValues) {
        if (!agent) {
            return;
        }
        setSavingEdit(true);
        setError(null);
        try {
            const kind = values.runtimeKind ?? AGENT_RUNTIME.REACT;
            const isReact = kind === AGENT_RUNTIME.REACT;
            await updateAgent(agent.id, buildUpsertAgentRequestBody(values, isReact), {
                timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS,
            });
            message.success("已保存");
            const fresh = await getAgent(agent.id, {timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS});
            setAgent(fresh);
            form.setFieldsValue({modelId: fresh.modelId});
        } catch (e) {
            setError(e);
        } finally {
            setSavingEdit(false);
        }
    }

    async function onRun(values: RunAgentForm) {
        if (!agent) {
            return;
        }
        setError(null);
        setRunning(true);
        setRunOut(null);
        runAbortRef.current?.abort();
        const runAc = new AbortController();
        runAbortRef.current = runAc;
        try {
            const options = buildRunOptions({
                temperature: values.temperature,
                maxTokens: values.maxTokens,
                topP: values.topP,
            });
            const body: Record<string, unknown> = {
                input: values.input,
            };
            const ns = values.memoryNamespace?.trim();
            if (ns) {
                body.memoryNamespace = ns;
            }
            const mid = values.modelId?.trim();
            if (mid) {
                body.modelId = mid;
            }
            if (options !== undefined) {
                body.options = options;
            }
            const data = await runAgent(agent.id, body, {signal: runAc.signal});
            setRunOut(data);
        } catch (e) {
            if (!isAbortError(e)) {
                setError(e);
            }
        } finally {
            const stillThisRun = runAbortRef.current === runAc;
            if (stillThisRun) {
                runAbortRef.current = null;
                setRunning(false);
            }
        }
    }

    return (
        <AppLayout>
            <PageShell>
                <PageHeaderBlock
                    icon={<RobotOutlined/>}
                    backHref="/agents"
                    title="智能体详情"
                    subtitle={
                        agent ? (
                            <>
                                ID：<Typography.Text code>{agent.id}</Typography.Text>
                            </>
                        ) : (
                            "加载中…"
                        )
                    }
                />

                <ErrorAlert error={error}/>

                <SectionCard title="配置快照" loading={loading}>
                    {agent ? (
                        <Descriptions column={1} size="small" labelStyle={{width: 140}}>
                            <Descriptions.Item label="名称">{agent.name}</Descriptions.Item>
                            <Descriptions.Item
                                label="运行时形态">{runtimeKindLabel(agent.runtimeKind)}</Descriptions.Item>
                            {agent.runtimeKind === AGENT_RUNTIME.REACT ? (
                                <Descriptions.Item label="ReAct maxIters">
                                    {typeof agent.maxReactIters === "number" ? agent.maxReactIters : "—"}
                                </Descriptions.Item>
                            ) : null}
                            <Descriptions.Item label="默认模型">
                                <Space orientation="vertical" size={4}>
                                    <Typography.Text strong>{agent.modelDisplayName ?? "—"}</Typography.Text>
                                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                                        {agent.modelProvider && agent.modelModelKey
                                            ? `${agent.modelProvider} / ${agent.modelModelKey}`
                                            : null}
                                    </Typography.Text>
                                    {agent.modelConfigSummary ? (
                                        <Typography.Text type="secondary" style={{fontSize: 12}} ellipsis>
                                            参数摘要：{agent.modelConfigSummary}
                                        </Typography.Text>
                                    ) : null}
                                    <Link href={`/models/${agent.modelId}`}>
                                        <Typography.Text code copyable>
                                            {agent.modelId}
                                        </Typography.Text>
                                    </Link>
                                </Space>
                            </Descriptions.Item>
                            <Descriptions.Item label="创建时间">{agent.createdAt ?? "-"}</Descriptions.Item>
                            <Descriptions.Item label="已绑定的工具">
                                {agent.toolIds && agent.toolIds.length > 0 ? (
                                    <Space wrap>
                                        {agent.toolIds.map((tid) => (
                                            <Link key={tid} href={`/tools/${tid}`} title={tid}>
                                                <Tag style={{cursor: "pointer"}}>
                                                    {tid.length > 16 ? `${tid.slice(0, 14)}…` : tid}
                                                </Tag>
                                            </Link>
                                        ))}
                                    </Space>
                                ) : (
                                    <Typography.Text type="secondary">未绑定</Typography.Text>
                                )}
                            </Descriptions.Item>
                            <Descriptions.Item label="记忆策略">
                                {agent.memoryPolicyId ? (
                                    <Space orientation="vertical" size={4}>
                                        <Typography.Text code copyable>{agent.memoryPolicyId}</Typography.Text>
                                        {agent.memoryPolicyName ? (
                                            <Typography.Text>{agent.memoryPolicyName}</Typography.Text>
                                        ) : null}
                                        {agent.memoryPolicyOwnerScope ? (
                                            <Typography.Text type="secondary" style={{fontSize: 12}}>
                                                owner_scope：{agent.memoryPolicyOwnerScope}
                                            </Typography.Text>
                                        ) : null}
                                        <Link href={`/memory-policies/${agent.memoryPolicyId}`}>查看策略与条目</Link>
                                    </Space>
                                ) : (
                                    <Typography.Text type="secondary">未绑定</Typography.Text>
                                )}
                            </Descriptions.Item>
                            <Descriptions.Item label="知识库策略">
                                <pre style={{
                                    margin: 0,
                                    whiteSpace: "pre-wrap",
                                }}>{stringifyPretty(agent.knowledgeBasePolicy ?? {})}</pre>
                            </Descriptions.Item>
                            <Descriptions.Item label="系统提示词">
                                <pre style={{margin: 0, whiteSpace: "pre-wrap"}}>{agent.systemPrompt}</pre>
                            </Descriptions.Item>
                        </Descriptions>
                    ) : (
                        <Typography.Text type="secondary">未加载到数据</Typography.Text>
                    )}
                </SectionCard>

                {agent ? (
                    <SectionCard title="编辑配置">
                        <Typography.Paragraph type="secondary" style={{marginTop: 0}}>
                            全量更新智能体（与创建页字段一致）。可在此<strong>改绑记忆策略</strong>、调整工具与知识库策略。
                        </Typography.Paragraph>
                        <Form<UpsertAgentFormValues>
                            form={editForm}
                            layout="vertical"
                            onFinish={onSaveEdit}
                            initialValues={{
                                runtimeKind: AGENT_RUNTIME.REACT,
                                maxReactIters: 10,
                                kbEnabled: false,
                                kbTopK: 5,
                                kbScoreThreshold: 0.25,
                                toolIds: [],
                            }}
                        >
                            <Form.Item
                                name="runtimeKind"
                                label="运行时形态"
                                rules={[{required: true, message: "请选择"}]}
                            >
                                <Select
                                    options={AGENT_RUNTIME_OPTIONS.map((o) => ({
                                        value: o.value,
                                        label: `${o.title}（${o.badge}）`,
                                    }))}
                                />
                            </Form.Item>
                            {editIsReact ? (
                                <Form.Item
                                    name="maxReactIters"
                                    label="ReAct 最大迭代步数"
                                    rules={[{type: "number", min: 1, max: 64, message: "范围 1–64"}]}
                                >
                                    <InputNumber min={1} max={64} style={{width: 200}}/>
                                </Form.Item>
                            ) : (
                                <Alert
                                    type="warning"
                                    showIcon
                                    style={{marginBottom: 16}}
                                    title="对话模式"
                                    description="将不保存工具绑定；运行时 maxIters 固定为 1。"
                                />
                            )}
                            <Form.Item name="name" label="名称" rules={[{required: true, message: "必填"}]}>
                                <Input/>
                            </Form.Item>
                            <Form.Item name="systemPrompt" label="系统提示词"
                                       rules={[{required: true, message: "必填"}]}>
                                <Input.TextArea rows={6}/>
                            </Form.Item>
                            <Form.Item name="modelId" label="默认聊天模型" rules={[{required: true, message: "必选"}]}>
                                <Select
                                    showSearch
                                    loading={loadingRefs}
                                    options={toModelSelectOptions(chatModelRows)}
                                    popupMatchSelectWidth={520}
                                    filterOption={(input, option) =>
                                        filterModelSelectOption(input, option as { searchText?: string })
                                    }
                                />
                            </Form.Item>
                            {editIsReact ? (
                                <>
                                    <Divider plain/>
                                    <Form.Item name="toolIds" label="可调用的工具">
                                        <Select
                                            mode="multiple"
                                            allowClear
                                            loading={loadingRefs}
                                            options={toolOptions}
                                            optionFilterProp="label"
                                            popupMatchSelectWidth={520}
                                            maxTagCount="responsive"
                                        />
                                    </Form.Item>
                                </>
                            ) : null}
                            <Divider plain/>
                            <Form.Item name="memoryPolicyId" label="记忆策略">
                                <Select
                                    allowClear
                                    showSearch
                                    loading={loadingRefs}
                                    placeholder="不启用则留空"
                                    options={memoryPolicyOptions}
                                    popupMatchSelectWidth={520}
                                    filterOption={(input, option) =>
                                        filterMemoryPolicySelectOption(input, option as { searchText?: string })
                                    }
                                />
                            </Form.Item>
                            <Divider plain/>
                            <Form.Item name="kbEnabled" label="启用知识库检索" valuePropName="checked">
                                <Switch/>
                            </Form.Item>
                            <Form.Item
                                noStyle
                                shouldUpdate={(prev, cur) => prev.kbEnabled !== cur.kbEnabled}
                            >
                                {({getFieldValue}) =>
                                    getFieldValue("kbEnabled") ? (
                                        <>
                                            <Form.Item
                                                name="kbCollectionIds"
                                                label="知识集合"
                                                rules={[{required: true, message: "请至少选择一个集合"}]}
                                            >
                                                <Select
                                                    mode="multiple"
                                                    allowClear
                                                    loading={loadingRefs}
                                                    options={collectionOptions}
                                                    optionFilterProp="label"
                                                />
                                            </Form.Item>
                                            <Form.Item name="kbTopK" label="知识片段召回条数">
                                                <InputNumber min={1} max={50} style={{width: "100%"}}/>
                                            </Form.Item>
                                            <Form.Item name="kbScoreThreshold" label="知识相似度阈值">
                                                <InputNumber
                                                    min={0}
                                                    max={1}
                                                    step={0.05}
                                                    style={{width: "100%"}}
                                                />
                                            </Form.Item>
                                            <Form.Item name="kbEmbeddingModelId" label="嵌入模型覆盖（可选）">
                                                <Select
                                                    allowClear
                                                    showSearch
                                                    placeholder="选用 Embedding 模型配置"
                                                    options={toModelSelectOptions(embeddingModelRows)}
                                                    popupMatchSelectWidth={520}
                                                    filterOption={(input, option) =>
                                                        filterModelSelectOption(input, option as {
                                                            searchText?: string
                                                        })
                                                    }
                                                />
                                            </Form.Item>
                                        </>
                                    ) : null
                                }
                            </Form.Item>
                            <Form.Item style={{marginTop: 16}}>
                                <Button type="primary" htmlType="submit" loading={savingEdit}>
                                    保存
                                </Button>
                            </Form.Item>
                        </Form>
                    </SectionCard>
                ) : null}

                <SectionCard title="试运行（同步返回）">
                    <Typography.Paragraph type="secondary" style={{marginTop: 0}}>
                        下方可填写用户输入；可选覆盖本次使用的模型与常见推理参数（与模型配置合并，此处优先）。
                        更多参数请在「模型」中调整默认配置。
                        {agent?.runtimeKind === AGENT_RUNTIME.CHAT ? (
                            <>
                                {" "}
                                当前为<strong>对话</strong>形态：运行时<strong>不挂载工具</strong>且 maxIters=1。
                            </>
                        ) : null}
                    </Typography.Paragraph>
                    <Form<RunAgentForm> form={form} layout="vertical" onFinish={onRun}>
                        <Form.Item
                            name="modelId"
                            label="本次使用的模型"
                            extra="可选：不选则使用上方配置的默认模型。"
                        >
                            <Select
                                allowClear
                                showSearch
                                placeholder={
                                    agent?.modelDisplayName
                                        ? `默认：${agent.modelDisplayName}`
                                        : "默认使用智能体绑定配置"
                                }
                                options={toModelSelectOptions(chatModelRows)}
                                popupMatchSelectWidth={520}
                                filterOption={(input, option) =>
                                    filterModelSelectOption(input, option as { searchText?: string })
                                }
                            />
                        </Form.Item>
                        <Typography.Text strong style={{display: "block", marginBottom: 8}}>
                            推理参数（可选，覆盖模型默认值）
                        </Typography.Text>
                        <Row gutter={16}>
                            <Col xs={24} sm={8}>
                                <Form.Item name="temperature" label="温度 temperature">
                                    <InputNumber
                                        min={0}
                                        max={2}
                                        step={0.1}
                                        style={{width: "100%"}}
                                        placeholder="默认用模型配置"
                                    />
                                </Form.Item>
                            </Col>
                            <Col xs={24} sm={8}>
                                <Form.Item name="maxTokens" label="最大生成长度 maxTokens">
                                    <InputNumber min={1} max={128000} style={{width: "100%"}}
                                                 placeholder="默认用模型配置"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} sm={8}>
                                <Form.Item name="topP" label="核采样 topP">
                                    <InputNumber
                                        min={0}
                                        max={1}
                                        step={0.05}
                                        style={{width: "100%"}}
                                        placeholder="默认用模型配置"
                                    />
                                </Form.Item>
                            </Col>
                        </Row>
                        <Form.Item name="input" label="用户输入" rules={[{required: true, message: "请输入内容"}]}>
                            <Input.TextArea rows={5} placeholder="模拟用户消息"/>
                        </Form.Item>
                        <Form.Item>
                            <Button type="primary" htmlType="submit" loading={running} disabled={!agent}>
                                运行
                            </Button>
                        </Form.Item>
                    </Form>
                    {runOut ? (
                        <>
                            <Typography.Text strong style={{display: "block", marginBottom: 8}}>
                                模型输出
                            </Typography.Text>
                            <pre
                                style={{
                                    margin: 0,
                                    whiteSpace: "pre-wrap",
                                    padding: 12,
                                    background: "var(--app-primary-soft, rgba(22,119,255,0.06))",
                                    borderRadius: "var(--app-radius-sm, 8px)",
                                    border: "1px solid var(--app-border)",
                                }}
                            >
                                {runOut.output}
                            </pre>
                            {runOut.memory ? (
                                <>
                                    <Typography.Text strong style={{display: "block", marginTop: 16, marginBottom: 8}}>
                                        记忆侧预览（调试）
                                    </Typography.Text>
                                    {runOut.memory.implementationWarnings &&
                                    runOut.memory.implementationWarnings.length > 0 ? (
                                        <Alert
                                            type="warning"
                                            showIcon
                                            style={{marginBottom: 12}}
                                            message="策略能力提示（与当前实现一致）"
                                            description={
                                                <ul style={{margin: 0, paddingLeft: 20}}>
                                                    {runOut.memory.implementationWarnings.map((w) => (
                                                        <li key={w}>{w}</li>
                                                    ))}
                                                </ul>
                                            }
                                        />
                                    ) : null}
                                    <Descriptions column={1} size="small" bordered>
                                        <Descriptions.Item label="策略">
                                            {runOut.memory.memoryPolicyName ?? "—"}{" "}
                                            {runOut.memory.memoryPolicyId ? (
                                                <Typography.Text code copyable>
                                                    {runOut.memory.memoryPolicyId}
                                                </Typography.Text>
                                            ) : null}
                                        </Descriptions.Item>
                                        <Descriptions.Item label="owner_scope">
                                            {runOut.memory.ownerScope ?? "—"}
                                        </Descriptions.Item>
                                        <Descriptions.Item label="retrieval / write">
                                            {runOut.memory.retrievalMode ?? "—"} / {runOut.memory.writeMode ?? "—"}
                                        </Descriptions.Item>
                                        <Descriptions.Item label="memoryNamespace">
                                            {runOut.memory.memoryNamespace ?? "（未指定）"}
                                        </Descriptions.Item>
                                        <Descriptions.Item label="粗略摘要上限（解析后）">
                                            {typeof runOut.memory.roughSummaryMaxCharsResolved === "number"
                                                ? runOut.memory.roughSummaryMaxCharsResolved
                                                : "—"}
                                        </Descriptions.Item>
                                        <Descriptions.Item label="预览排序">
                                            {runOut.memory.keywordPreviewSort === "TRGM_WORD_SIMILARITY"
                                                ? "词相似度（pg_trgm，与当前输入非空一致）"
                                                : runOut.memory.keywordPreviewSort === "RECENCY"
                                                    ? "仅按时间（当前输入为空）"
                                                    : (runOut.memory.keywordPreviewSort ?? "—")}
                                        </Descriptions.Item>
                                        <Descriptions.Item label="预览命中条数">
                                            {runOut.memory.previewHitCount ?? 0}
                                        </Descriptions.Item>
                                    </Descriptions>
                                    <pre
                                        style={{
                                            marginTop: 8,
                                            whiteSpace: "pre-wrap",
                                            padding: 12,
                                            background: "rgba(0,0,0,0.02)",
                                            borderRadius: "var(--app-radius-sm, 8px)",
                                            border: "1px solid var(--app-border)",
                                            maxHeight: 280,
                                            overflow: "auto",
                                        }}
                                    >
                                        {runOut.memory.previewText || "（无命中）"}
                                    </pre>
                                </>
                            ) : null}
                        </>
                    ) : (
                        <Typography.Text type="secondary">尚未运行</Typography.Text>
                    )}
                </SectionCard>

                {agent ? (
                    <SectionCard title="快捷跳转">
                        <Space wrap>
                            <Link href={`/models/${agent.modelId}`}>查看绑定模型</Link>
                            <Typography.Text type="secondary">|</Typography.Text>
                            <Link href="/tools">工具管理</Link>
                            <Typography.Text type="secondary">|</Typography.Text>
                            <Link href="/memory-policies">记忆策略</Link>
                            <Typography.Text type="secondary">|</Typography.Text>
                            <Link href="/kb">知识库</Link>
                        </Space>
                    </SectionCard>
                ) : null}
            </PageShell>
        </AppLayout>
    );
}
