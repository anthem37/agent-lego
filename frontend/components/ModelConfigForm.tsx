"use client";

import {Button, Collapse, Input, InputNumber, Select, Space, Switch, Typography} from "antd";
import React from "react";

import {objectToPairs, pairsToObject, type StringPair} from "@/lib/model-config";
import {CONFIG_KEY_TITLE} from "@/lib/model-config-labels";

const TEMPERATURE_OPTIONS = [
    {label: "更稳定、偏确定（0.1）", value: 0.1},
    {label: "偏保守（0.3）", value: 0.3},
    {label: "适中（0.5）", value: 0.5},
    {label: "均衡（0.7）", value: 0.7},
    {label: "更灵活（0.9）", value: 0.9},
    {label: "更有创意（1.0）", value: 1.0},
    {label: "更高随机性（1.2）", value: 1.2},
];

const TOP_P_OPTIONS = [
    {label: "核采样偏窄（0.1）", value: 0.1},
    {label: "适中（0.5）", value: 0.5},
    {label: "较宽（0.9）", value: 0.9},
    {label: "接近全量（1.0）", value: 1.0},
];

const TOP_K_OPTIONS = [
    {label: "很小（1）", value: 1},
    {label: "小（5）", value: 5},
    {label: "中（20）", value: 20},
    {label: "较大（50）", value: 50},
    {label: "大（100）", value: 100},
];

const MAX_TOKEN_OPTIONS = [
    {label: "256", value: 256},
    {label: "512", value: 512},
    {label: "1024", value: 1024},
    {label: "2048", value: 2048},
    {label: "4096", value: 4096},
    {label: "8192", value: 8192},
    {label: "16384", value: 16384},
];

const ENDPOINT_PATH_OPTIONS = [
    {label: "对话补全接口 · /v1/chat/completions", value: "/v1/chat/completions"},
    {label: "Responses 接口 · /v1/responses", value: "/v1/responses"},
    {label: "传统补全接口 · /v1/completions", value: "/v1/completions"},
    {label: "Embedding 接口 · /v1/embeddings", value: "/v1/embeddings"},
];

const ENCODING_FORMAT_OPTIONS = [
    {label: "float（向量数值）", value: "float"},
    {label: "base64（向量 base64）", value: "base64"},
];

const AGENTSCOPE_ADVANCED_KEYS = [
    "stream",
    "frequencyPenalty",
    "presencePenalty",
    "thinkingBudget",
    "reasoningEffort",
    "toolChoice",
    "executionConfig",
];

type Props = {
    value?: Record<string, unknown>;
    onChange?: (next: Record<string, unknown>) => void;
    supportedKeys: string[];
};

type PairsBlockProps = {
    title: string;
    hint: string;
    pairs: StringPair[];
    onChangePairs: (p: StringPair[]) => void;
};

/**
 * 键值对列表编辑（用于 additionalHeaders 等）。
 */
function ConfigKeyValuePairsBlock(props: PairsBlockProps) {
    const {title, hint, pairs, onChangePairs} = props;
    const list = pairs.length > 0 ? pairs : [{key: "", value: ""}];

    return (
        <div>
            <Typography.Text strong>{title}</Typography.Text>
            <Typography.Paragraph type="secondary" style={{marginBottom: 8, marginTop: 4}}>
                {hint}
            </Typography.Paragraph>
            <Space orientation="vertical" size={8} style={{width: "100%"}}>
                {list.map((row, idx) => (
                    <Space.Compact key={idx} style={{width: "100%"}}>
                        <Input
                            placeholder="参数名"
                            style={{width: "38%"}}
                            value={row.key}
                            onChange={(e) => {
                                const next = [...list];
                                next[idx] = {...row, key: e.target.value};
                                onChangePairs(next);
                            }}
                        />
                        <Input
                            placeholder="参数值"
                            style={{width: "62%"}}
                            value={row.value}
                            onChange={(e) => {
                                const next = [...list];
                                next[idx] = {...row, value: e.target.value};
                                onChangePairs(next);
                            }}
                        />
                    </Space.Compact>
                ))}
                <Button
                    type="dashed"
                    block
                    onClick={() => onChangePairs([...list, {key: "", value: ""}])}
                >
                    添加一行
                </Button>
            </Space>
        </div>
    );
}

function readNumber(v: unknown): number | null {
    if (v === undefined || v === null || v === "") {
        return null;
    }
    if (typeof v === "number" && !Number.isNaN(v)) {
        return v;
    }
    const n = Number(v);
    return Number.isNaN(n) ? null : n;
}

function FieldHeading(props: { apiKey: string }) {
    const zh = CONFIG_KEY_TITLE[props.apiKey];
    return (
        <div style={{marginBottom: 4}}>
            <Typography.Text strong>{zh ?? props.apiKey}</Typography.Text>
            {zh ? (
                <Typography.Text type="secondary" style={{marginLeft: 8, fontSize: 12}}>
                    {props.apiKey}
                </Typography.Text>
            ) : null}
        </div>
    );
}

/**
 * 模型 config 可视化编辑：以下拉、数字输入、键值对为主，避免手写 JSON。
 */
export function ModelConfigForm(props: Props) {
    const {value = {}, onChange, supportedKeys} = props;
    const has = React.useCallback((k: string) => supportedKeys.includes(k), [supportedKeys]);

    const patch = React.useCallback(
        (partial: Record<string, unknown>) => {
            const next: Record<string, unknown> = {...value};
            for (const [k, v] of Object.entries(partial)) {
                if (v === undefined || v === null || v === "") {
                    delete next[k];
                } else {
                    next[k] = v;
                }
            }
            onChange?.(next);
        },
        [value, onChange],
    );

    const temperature = readNumber(value.temperature);
    const topP = readNumber(value.topP);
    const topK = readNumber(value.topK);
    const maxTokens = readNumber(value.maxTokens);
    const maxCompletionTokens = readNumber(value.maxCompletionTokens);
    const seed = readNumber(value.seed);
    const dimensions = readNumber(value.dimensions);
    const endpointPath = typeof value.endpointPath === "string" ? value.endpointPath : "";
    const encodingFormat = typeof value.encodingFormat === "string" ? value.encodingFormat : undefined;

    const frequencyPenalty = readNumber(value.frequencyPenalty);
    const presencePenalty = readNumber(value.presencePenalty);
    const thinkingBudget = readNumber(value.thinkingBudget);
    const reasoningEffort =
        typeof value.reasoningEffort === "string" && value.reasoningEffort.trim() !== ""
            ? value.reasoningEffort.trim()
            : undefined;

    const executionConfigObj =
        value.executionConfig && typeof value.executionConfig === "object" && !Array.isArray(value.executionConfig)
            ? (value.executionConfig as Record<string, unknown>)
            : {};

    const toolChoiceState = React.useMemo(() => {
        const tc = value.toolChoice;
        if (tc === undefined || tc === null) {
            return {mode: "unset" as const, toolName: ""};
        }
        if (typeof tc === "string") {
            const t = tc.trim().toLowerCase();
            if (t === "auto" || t === "none" || t === "required") {
                return {mode: t as "auto" | "none" | "required", toolName: ""};
            }
            return {mode: "unset" as const, toolName: ""};
        }
        if (typeof tc === "object" && !Array.isArray(tc)) {
            const o = tc as Record<string, unknown>;
            const tn = typeof o.toolName === "string" ? o.toolName : "";
            if (tn.trim()) {
                return {mode: "specific" as const, toolName: tn.trim()};
            }
            const m = typeof o.mode === "string" ? o.mode.trim().toLowerCase() : "";
            if (m === "auto" || m === "none" || m === "required") {
                return {mode: m as "auto" | "none" | "required", toolName: ""};
            }
        }
        return {mode: "unset" as const, toolName: ""};
    }, [value.toolChoice]);

    function patchExecution(key: string, v: number | null | undefined) {
        const next: Record<string, unknown> = {...executionConfigObj};
        if (v === null || v === undefined) {
            delete next[key];
        } else {
            next[key] = v;
        }
        if (Object.keys(next).length === 0) {
            patch({executionConfig: undefined});
        } else {
            patch({executionConfig: next});
        }
    }

    function setToolChoice(mode: string, toolName: string) {
        if (mode === "unset") {
            patch({toolChoice: undefined});
        } else if (mode === "specific") {
            const t = toolName.trim();
            patch({toolChoice: t ? {mode: "specific", toolName: t} : undefined});
        } else {
            patch({toolChoice: mode});
        }
    }

    const hasAdvanced = AGENTSCOPE_ADVANCED_KEYS.some((k) => has(k));

    const headerPairs = React.useMemo(() => objectToPairs(value.additionalHeaders), [value.additionalHeaders]);
    const bodyPairs = React.useMemo(() => objectToPairs(value.additionalBodyParams), [value.additionalBodyParams]);
    const queryPairs = React.useMemo(() => objectToPairs(value.additionalQueryParams), [value.additionalQueryParams]);

    function updatePairs(
        key: "additionalHeaders" | "additionalBodyParams" | "additionalQueryParams",
        pairs: StringPair[],
    ) {
        const obj = pairsToObject(pairs);
        if (Object.keys(obj).length === 0) {
            patch({[key]: undefined});
        } else {
            patch({[key]: obj});
        }
    }

    const basicPanel = (
        <Space orientation="vertical" size={16} style={{width: "100%"}}>
            {has("dimensions") ? (
                <div>
                    <FieldHeading apiKey="dimensions"/>
                    <Space.Compact style={{width: "100%", marginTop: 8}}>
                        <InputNumber
                            style={{width: "100%", minWidth: 120}}
                            min={1}
                            step={1}
                            placeholder="例如：1536"
                            value={dimensions ?? undefined}
                            onChange={(v) => patch({dimensions: v === null ? undefined : v})}
                        />
                    </Space.Compact>
                </div>
            ) : null}

            {has("encodingFormat") ? (
                <div>
                    <FieldHeading apiKey="encodingFormat"/>
                    <Space.Compact style={{width: "100%", marginTop: 8}}>
                        <Select
                            allowClear
                            placeholder="选择编码格式"
                            style={{width: "100%"}}
                            value={ENCODING_FORMAT_OPTIONS.some((o) => o.value === encodingFormat) ? encodingFormat : undefined}
                            options={ENCODING_FORMAT_OPTIONS}
                            onChange={(v) => patch({encodingFormat: v === undefined || v === null ? undefined : v})}
                        />
                    </Space.Compact>
                </div>
            ) : null}

            {has("temperature") ? (
                <div>
                    <FieldHeading apiKey="temperature"/>
                    <Typography.Paragraph type="secondary" style={{marginBottom: 8, marginTop: 0}}>
                        数值越大通常越随机；可先用下拉快捷项，再微调数字。
                    </Typography.Paragraph>
                    <Space.Compact style={{width: "100%"}}>
                        <Select
                            allowClear
                            placeholder="快捷选择"
                            style={{width: 200}}
                            value={TEMPERATURE_OPTIONS.some((o) => o.value === temperature) ? temperature : undefined}
                            options={TEMPERATURE_OPTIONS}
                            onChange={(v) => {
                                if (v === undefined || v === null) {
                                    patch({temperature: undefined});
                                } else {
                                    patch({temperature: v});
                                }
                            }}
                        />
                        <InputNumber
                            style={{width: "100%", minWidth: 120}}
                            min={0}
                            max={2}
                            step={0.05}
                            placeholder="或手动输入 0～2"
                            value={temperature ?? undefined}
                            onChange={(v) => patch({temperature: v === null ? undefined : v})}
                        />
                    </Space.Compact>
                </div>
            ) : null}

            {has("topP") ? (
                <div>
                    <FieldHeading apiKey="topP"/>
                    <Space.Compact style={{width: "100%", marginTop: 8}}>
                        <Select
                            allowClear
                            placeholder="快捷选择"
                            style={{width: 200}}
                            value={TOP_P_OPTIONS.some((o) => o.value === topP) ? topP : undefined}
                            options={TOP_P_OPTIONS}
                            onChange={(v) => patch({topP: v === undefined || v === null ? undefined : v})}
                        />
                        <InputNumber
                            style={{width: "100%", minWidth: 120}}
                            min={0}
                            max={1}
                            step={0.05}
                            placeholder="0～1"
                            value={topP ?? undefined}
                            onChange={(v) => patch({topP: v === null ? undefined : v})}
                        />
                    </Space.Compact>
                </div>
            ) : null}

            {has("topK") ? (
                <div>
                    <FieldHeading apiKey="topK"/>
                    <Space.Compact style={{width: "100%", marginTop: 8}}>
                        <Select
                            allowClear
                            placeholder="快捷选择"
                            style={{width: 200}}
                            value={TOP_K_OPTIONS.some((o) => o.value === topK) ? topK : undefined}
                            options={TOP_K_OPTIONS}
                            onChange={(v) => patch({topK: v === undefined || v === null ? undefined : v})}
                        />
                        <InputNumber
                            style={{width: "100%", minWidth: 120}}
                            min={1}
                            step={1}
                            placeholder="正整数"
                            value={topK ?? undefined}
                            onChange={(v) => patch({topK: v === null ? undefined : v})}
                        />
                    </Space.Compact>
                </div>
            ) : null}

            {has("maxTokens") ? (
                <div>
                    <FieldHeading apiKey="maxTokens"/>
                    <Space.Compact style={{width: "100%", marginTop: 8}}>
                        <Select
                            allowClear
                            placeholder="常用上限"
                            style={{width: 200}}
                            value={MAX_TOKEN_OPTIONS.some((o) => o.value === maxTokens) ? maxTokens : undefined}
                            options={MAX_TOKEN_OPTIONS}
                            onChange={(v) => patch({maxTokens: v === undefined || v === null ? undefined : v})}
                        />
                        <InputNumber
                            style={{width: "100%", minWidth: 120}}
                            min={1}
                            step={64}
                            placeholder="自定义"
                            value={maxTokens ?? undefined}
                            onChange={(v) => patch({maxTokens: v === null ? undefined : v})}
                        />
                    </Space.Compact>
                </div>
            ) : null}

            {has("maxCompletionTokens") ? (
                <div>
                    <FieldHeading apiKey="maxCompletionTokens"/>
                    <Space.Compact style={{width: "100%", marginTop: 8}}>
                        <Select
                            allowClear
                            placeholder="常用上限"
                            style={{width: 200}}
                            value={
                                MAX_TOKEN_OPTIONS.some((o) => o.value === maxCompletionTokens)
                                    ? maxCompletionTokens
                                    : undefined
                            }
                            options={MAX_TOKEN_OPTIONS}
                            onChange={(v) =>
                                patch({maxCompletionTokens: v === undefined || v === null ? undefined : v})
                            }
                        />
                        <InputNumber
                            style={{width: "100%", minWidth: 120}}
                            min={1}
                            step={64}
                            placeholder="自定义"
                            value={maxCompletionTokens ?? undefined}
                            onChange={(v) => patch({maxCompletionTokens: v === null ? undefined : v})}
                        />
                    </Space.Compact>
                </div>
            ) : null}

            {has("seed") ? (
                <div>
                    <FieldHeading apiKey="seed"/>
                    <Typography.Paragraph type="secondary" style={{marginBottom: 8, marginTop: 0}}>
                        默认不指定；需要可复现输出时选择「指定种子」并填写整数。
                    </Typography.Paragraph>
                    <Space.Compact style={{width: "100%"}}>
                        <Select
                            style={{width: 200}}
                            placeholder="是否指定"
                            value={value.seed === undefined || value.seed === null ? "unset" : "set"}
                            options={[
                                {label: "不指定（推荐）", value: "unset"},
                                {label: "指定整数种子", value: "set"},
                            ]}
                            onChange={(v) => {
                                if (v === "unset") {
                                    patch({seed: undefined});
                                } else {
                                    patch({seed: readNumber(value.seed) ?? 42});
                                }
                            }}
                        />
                        <InputNumber
                            style={{width: "100%", minWidth: 120}}
                            step={1}
                            placeholder="整数，例如 42"
                            disabled={value.seed === undefined || value.seed === null}
                            value={seed ?? undefined}
                            onChange={(v) => patch({seed: v === null ? undefined : v})}
                        />
                    </Space.Compact>
                </div>
            ) : null}
        </Space>
    );

    const advancedPanel = (
        <Space orientation="vertical" size={16} style={{width: "100%"}}>
            {has("stream") ? (
                <div>
                    <FieldHeading apiKey="stream"/>
                    <Typography.Paragraph type="secondary" style={{marginBottom: 8, marginTop: 0}}>
                        对应 AgentScope <Typography.Text code>GenerateOptions.stream</Typography.Text>
                        ；关闭时不传该字段，由模型默认行为决定。
                    </Typography.Paragraph>
                    <Switch
                        checked={value.stream === true}
                        checkedChildren="流式"
                        unCheckedChildren="默认"
                        onChange={(checked) => patch({stream: checked ? true : undefined})}
                    />
                </div>
            ) : null}

            {has("frequencyPenalty") ? (
                <div>
                    <FieldHeading apiKey="frequencyPenalty"/>
                    <InputNumber
                        style={{width: "100%", maxWidth: 280, marginTop: 8}}
                        min={-2}
                        max={2}
                        step={0.05}
                        placeholder="例如 0.0（OpenAI 等常用范围约 -2～2）"
                        value={frequencyPenalty ?? undefined}
                        onChange={(v) => patch({frequencyPenalty: v === null ? undefined : v})}
                    />
                </div>
            ) : null}

            {has("presencePenalty") ? (
                <div>
                    <FieldHeading apiKey="presencePenalty"/>
                    <InputNumber
                        style={{width: "100%", maxWidth: 280, marginTop: 8}}
                        min={-2}
                        max={2}
                        step={0.05}
                        placeholder="例如 0.0"
                        value={presencePenalty ?? undefined}
                        onChange={(v) => patch({presencePenalty: v === null ? undefined : v})}
                    />
                </div>
            ) : null}

            {has("thinkingBudget") ? (
                <div>
                    <FieldHeading apiKey="thinkingBudget"/>
                    <Typography.Paragraph type="secondary" style={{marginBottom: 8, marginTop: 0}}>
                        部分推理模型使用的思考资源上限（具体含义以厂商文档为准）。
                    </Typography.Paragraph>
                    <InputNumber
                        style={{width: "100%", maxWidth: 280}}
                        min={0}
                        step={1}
                        placeholder="正整数，可选"
                        value={thinkingBudget ?? undefined}
                        onChange={(v) => patch({thinkingBudget: v === null ? undefined : v})}
                    />
                </div>
            ) : null}

            {has("reasoningEffort") ? (
                <div>
                    <FieldHeading apiKey="reasoningEffort"/>
                    <Typography.Paragraph type="secondary" style={{marginBottom: 8, marginTop: 0}}>
                        常见取值如 <Typography.Text code>minimal</Typography.Text>、
                        <Typography.Text code>low</Typography.Text>、
                        <Typography.Text code>medium</Typography.Text>、
                        <Typography.Text code>high</Typography.Text>（以厂商文档为准）。
                    </Typography.Paragraph>
                    <Input
                        allowClear
                        style={{maxWidth: 360}}
                        placeholder="留空表示不传该字段"
                        value={reasoningEffort ?? ""}
                        onChange={(e) => {
                            const t = e.target.value.trim();
                            patch({reasoningEffort: t === "" ? undefined : t});
                        }}
                    />
                </div>
            ) : null}

            {has("toolChoice") ? (
                <div>
                    <FieldHeading apiKey="toolChoice"/>
                    <Typography.Paragraph type="secondary" style={{marginBottom: 8, marginTop: 0}}>
                        映射 AgentScope <Typography.Text code>ToolChoice</Typography.Text>
                        （Auto / None / Required / Specific）。
                    </Typography.Paragraph>
                    <Select
                        style={{width: "100%", maxWidth: 360, marginBottom: 8}}
                        value={toolChoiceState.mode}
                        options={[
                            {label: "不指定", value: "unset"},
                            {label: "auto（自动）", value: "auto"},
                            {label: "none（禁用工具）", value: "none"},
                            {label: "required（必须调用工具）", value: "required"},
                            {label: "specific（指定工具名）", value: "specific"},
                        ]}
                        onChange={(v) => setToolChoice(v ?? "unset", toolChoiceState.toolName)}
                    />
                    {toolChoiceState.mode === "specific" ? (
                        <Input
                            placeholder="工具名称 toolName，需与注册到 Toolkit 的名称一致"
                            value={toolChoiceState.toolName}
                            onChange={(e) => setToolChoice("specific", e.target.value)}
                        />
                    ) : null}
                </div>
            ) : null}

            {has("executionConfig") ? (
                <div>
                    <FieldHeading apiKey="executionConfig"/>
                    <Typography.Paragraph type="secondary" style={{marginBottom: 8, marginTop: 0}}>
                        对应 AgentScope <Typography.Text code>ExecutionConfig</Typography.Text>
                        ：HTTP 调用超时与重试退避（retryOn Predicate 无法 JSON 序列化，需在代码侧扩展）。
                    </Typography.Paragraph>
                    <Space orientation="vertical" size={12} style={{width: "100%"}}>
                        <Space.Compact style={{width: "100%", maxWidth: 400}}>
                            <Typography.Text style={{width: 140, lineHeight: "32px"}}>
                                {CONFIG_KEY_TITLE.timeoutSeconds}
                            </Typography.Text>
                            <InputNumber
                                style={{width: "100%"}}
                                min={0.1}
                                step={0.5}
                                placeholder="秒"
                                value={readNumber(executionConfigObj.timeoutSeconds) ?? undefined}
                                onChange={(v) => patchExecution("timeoutSeconds", v === null ? undefined : v)}
                            />
                        </Space.Compact>
                        <Space.Compact style={{width: "100%", maxWidth: 400}}>
                            <Typography.Text style={{width: 140, lineHeight: "32px"}}>
                                {CONFIG_KEY_TITLE.maxAttempts}
                            </Typography.Text>
                            <InputNumber
                                style={{width: "100%"}}
                                min={1}
                                step={1}
                                placeholder="次数"
                                value={readNumber(executionConfigObj.maxAttempts) ?? undefined}
                                onChange={(v) => patchExecution("maxAttempts", v === null ? undefined : v)}
                            />
                        </Space.Compact>
                        <Space.Compact style={{width: "100%", maxWidth: 400}}>
                            <Typography.Text style={{width: 140, lineHeight: "32px"}}>
                                {CONFIG_KEY_TITLE.initialBackoffSeconds}
                            </Typography.Text>
                            <InputNumber
                                style={{width: "100%"}}
                                min={0}
                                step={0.1}
                                placeholder="秒"
                                value={readNumber(executionConfigObj.initialBackoffSeconds) ?? undefined}
                                onChange={(v) => patchExecution("initialBackoffSeconds", v === null ? undefined : v)}
                            />
                        </Space.Compact>
                        <Space.Compact style={{width: "100%", maxWidth: 400}}>
                            <Typography.Text style={{width: 140, lineHeight: "32px"}}>
                                {CONFIG_KEY_TITLE.maxBackoffSeconds}
                            </Typography.Text>
                            <InputNumber
                                style={{width: "100%"}}
                                min={0}
                                step={0.5}
                                placeholder="秒"
                                value={readNumber(executionConfigObj.maxBackoffSeconds) ?? undefined}
                                onChange={(v) => patchExecution("maxBackoffSeconds", v === null ? undefined : v)}
                            />
                        </Space.Compact>
                        <Space.Compact style={{width: "100%", maxWidth: 400}}>
                            <Typography.Text style={{width: 140, lineHeight: "32px"}}>
                                {CONFIG_KEY_TITLE.backoffMultiplier}
                            </Typography.Text>
                            <InputNumber
                                style={{width: "100%"}}
                                min={0.1}
                                step={0.1}
                                placeholder="倍数"
                                value={readNumber(executionConfigObj.backoffMultiplier) ?? undefined}
                                onChange={(v) => patchExecution("backoffMultiplier", v === null ? undefined : v)}
                            />
                        </Space.Compact>
                    </Space>
                </div>
            ) : null}
        </Space>
    );

    const endpointPanel = has("endpointPath") ? (
        <Space orientation="vertical" size={12} style={{width: "100%"}}>
            <FieldHeading apiKey="endpointPath"/>
            <Typography.Paragraph type="secondary" style={{marginBottom: 0}}>
                多数兼容网关使用「对话补全」路径；若网关文档要求其他路径，可下拉选择或手动填写。
            </Typography.Paragraph>
            <Select
                allowClear
                placeholder="选择常见路径"
                style={{width: "100%"}}
                value={ENDPOINT_PATH_OPTIONS.some((o) => o.value === endpointPath) ? endpointPath : undefined}
                options={ENDPOINT_PATH_OPTIONS}
                onChange={(v) => patch({endpointPath: v ?? undefined})}
            />
            <Input
                allowClear
                placeholder="或手动输入路径，例如 /v1/chat/completions"
                value={endpointPath}
                onChange={(e) => patch({endpointPath: e.target.value.trim() === "" ? undefined : e.target.value})}
            />
        </Space>
    ) : null;

    const extraPanel = (
        <Space orientation="vertical" size={20} style={{width: "100%"}}>
            {has("additionalHeaders") ? (
                <ConfigKeyValuePairsBlock
                    title={`${CONFIG_KEY_TITLE.additionalHeaders}（${"additionalHeaders"}）`}
                    hint="例如鉴权头、链路追踪 ID；将合并到实际 HTTP 请求头中。"
                    pairs={headerPairs}
                    onChangePairs={(p) => updatePairs("additionalHeaders", p)}
                />
            ) : null}
            {has("additionalBodyParams") ? (
                <ConfigKeyValuePairsBlock
                    title={`${CONFIG_KEY_TITLE.additionalBodyParams}（${"additionalBodyParams"}）`}
                    hint="会合并进请求 JSON 正文（值以字符串传递）；适合网关要求的扩展字段。"
                    pairs={bodyPairs}
                    onChangePairs={(p) => updatePairs("additionalBodyParams", p)}
                />
            ) : null}
            {has("additionalQueryParams") ? (
                <ConfigKeyValuePairsBlock
                    title={`${CONFIG_KEY_TITLE.additionalQueryParams}（${"additionalQueryParams"}）`}
                    hint="将拼接到请求 URL 的查询字符串上。"
                    pairs={queryPairs}
                    onChangePairs={(p) => updatePairs("additionalQueryParams", p)}
                />
            ) : null}
        </Space>
    );

    const items = [
        {
            key: "basic",
            label: "采样与输出长度",
            children: basicPanel,
        },
        ...(hasAdvanced
            ? [
                {
                    key: "agentscope",
                    label: "AgentScope 高级（惩罚 / 流式 / 工具 / 重试）",
                    children: advancedPanel,
                },
            ]
            : []),
        ...(endpointPanel
            ? [
                {
                    key: "endpoint",
                    label: "接口路径（兼容 OpenAI 类网关）",
                    children: endpointPanel,
                },
            ]
            : []),
        ...(has("additionalHeaders") || has("additionalBodyParams") || has("additionalQueryParams")
            ? [
                {
                    key: "extra",
                    label: "附加参数（请求头 / 正文 / 查询串）",
                    children: extraPanel,
                },
            ]
            : []),
    ];

    if (supportedKeys.length === 0) {
        return (
            <Typography.Text type="secondary">请先选择提供方（provider）后再配置参数。</Typography.Text>
        );
    }

    return (
        <div>
            <Space style={{marginBottom: 12}} wrap>
                <Button
                    size="small"
                    onClick={() => onChange?.({})}
                >
                    清空全部配置
                </Button>
                <Typography.Text type="secondary">
                    清空后保存：新建可不传 config；编辑会写入空对象以清除原配置。
                </Typography.Text>
            </Space>
            <Collapse defaultActiveKey={["basic"]} items={items}/>
        </div>
    );
}
