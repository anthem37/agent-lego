"use client";

import {Button, Collapse, Input, InputNumber, Select, Space, Typography} from "antd";
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
