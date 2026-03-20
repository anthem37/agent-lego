"use client";

import {Collapse, Descriptions, Table, Typography} from "antd";
import React from "react";

import {CONFIG_KEY_TITLE, configKeyTitle, formatScalarForDisplay} from "@/lib/model-config-labels";
import {stringifyPretty} from "@/lib/json";

/** 以键值表格展示的嵌套对象（含 AgentScope executionConfig） */
const OBJECT_KEYS = new Set([
    "additionalHeaders",
    "additionalBodyParams",
    "additionalQueryParams",
    "executionConfig",
    "toolChoice",
]);

type Props = {
    config?: Record<string, unknown> | null;
};

/**
 * 以中文标签展示模型 config，复杂结构用表格；底部可展开查看原始 JSON。
 */
export function ModelConfigDisplay(props: Props) {
    const {config} = props;
    if (!config || Object.keys(config).length === 0) {
        return <Typography.Text type="secondary">未配置默认推理参数（将使用服务端或模型默认行为）。</Typography.Text>;
    }

    const scalarEntries: [string, unknown][] = [];
    const objectEntries: [string, Record<string, unknown>][] = [];

    for (const [k, v] of Object.entries(config)) {
        if (OBJECT_KEYS.has(k) && v && typeof v === "object" && !Array.isArray(v)) {
            objectEntries.push([k, v as Record<string, unknown>]);
        } else {
            scalarEntries.push([k, v]);
        }
    }

    function renderLabel(key: string) {
        const zh = configKeyTitle(key);
        const mapped = CONFIG_KEY_TITLE[key];
        return (
            <span>
                {zh}
                {mapped ? (
                    <Typography.Text type="secondary" style={{marginLeft: 6, fontSize: 12}}>
                        {key}
                    </Typography.Text>
                ) : (
                    <Typography.Text type="secondary" style={{marginLeft: 6, fontSize: 12}}>
                        （自定义字段）
                    </Typography.Text>
                )}
            </span>
        );
    }

    return (
        <div>
            {scalarEntries.length > 0 ? (
                <Descriptions
                    column={1}
                    size="small"
                    bordered
                    style={{marginBottom: objectEntries.length > 0 ? 16 : 0}}
                >
                    {scalarEntries.map(([key, val]) => (
                        <Descriptions.Item key={key} label={renderLabel(key)}>
                            {formatScalarForDisplay(val)}
                        </Descriptions.Item>
                    ))}
                </Descriptions>
            ) : null}

            {objectEntries.map(([key, obj]) => {
                const rows = Object.entries(obj).map(([k, v]) => ({
                    key: k,
                    paramLabel: (
                        <span>
                            {configKeyTitle(k)}
                            <Typography.Text type="secondary" style={{marginLeft: 6, fontSize: 12}}>
                                {k}
                            </Typography.Text>
                        </span>
                    ),
                    value: formatScalarForDisplay(v),
                }));
                return (
                    <div key={key} style={{marginBottom: 16}}>
                        <Typography.Title level={5} style={{marginBottom: 8}}>
                            {configKeyTitle(key)}
                            <Typography.Text type="secondary"
                                             style={{marginLeft: 8, fontSize: 13, fontWeight: "normal"}}>
                                {key}
                            </Typography.Text>
                        </Typography.Title>
                        {rows.length === 0 ? (
                            <Typography.Text type="secondary">（无条目）</Typography.Text>
                        ) : (
                            <Table
                                size="small"
                                pagination={false}
                                rowKey="key"
                                columns={[
                                    {title: "参数", dataIndex: "paramLabel", width: "42%"},
                                    {title: "值", dataIndex: "value"},
                                ]}
                                dataSource={rows}
                            />
                        )}
                    </div>
                );
            })}

            <Collapse
                size="small"
                items={[
                    {
                        key: "raw",
                        label: "原始配置 JSON（供排查或对照接口文档）",
                        children: (
                            <pre style={{margin: 0, whiteSpace: "pre-wrap", fontSize: 12}}>
                                {stringifyPretty(config)}
                            </pre>
                        ),
                    },
                ]}
            />
        </div>
    );
}
