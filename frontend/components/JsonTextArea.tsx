"use client";

import {Button, Input, Space, Typography} from "antd";
import React from "react";

import {stringifyPretty} from "@/lib/json";

export function JsonTextArea(props: {
    value?: string;
    onChange?: (value: string) => void;
    placeholder?: string;
    rows?: number;
    sample?: Record<string, unknown>;
}) {
    const rows = props.rows ?? 8;
    const sample = props.sample;
    const [hint, setHint] = React.useState<string>("提示：请保持为 JSON object（不是数组/字符串）");

    function formatJson() {
        try {
            const next = stringifyPretty(JSON.parse(props.value ?? "{}"));
            props.onChange?.(next);
            setHint("已格式化 JSON");
        } catch {
            setHint("JSON 格式有误，无法格式化");
        }
    }

    function validateJson() {
        try {
            const parsed = JSON.parse(props.value ?? "{}");
            if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
                setHint("校验失败：需要 JSON object");
                return;
            }
            setHint("校验通过：JSON object");
        } catch {
            setHint("校验失败：JSON 语法错误");
        }
    }

    return (
        <Space orientation="vertical" size={8} style={{width: "100%"}}>
            <Input.TextArea
                value={props.value}
                rows={rows}
                onChange={(e) => props.onChange?.(e.target.value)}
                placeholder={props.placeholder ?? "请输入 JSON 对象，例如 {\"k\":\"v\"}"}
            />
            {sample ? (
                <Space size={8} wrap>
                    <Button size="small" onClick={formatJson}>
                        格式化
                    </Button>
                    <Button size="small" onClick={validateJson}>
                        校验
                    </Button>
                    <Button
                        size="small"
                        onClick={() => props.onChange?.(stringifyPretty(sample))}
                    >
                        填入示例
                    </Button>
                    <Typography.Text type="secondary">{hint}</Typography.Text>
                </Space>
            ) : (
                <Space size={8} wrap>
                    <Button size="small" onClick={formatJson}>
                        格式化
                    </Button>
                    <Button size="small" onClick={validateJson}>
                        校验
                    </Button>
                    <Typography.Text type="secondary">{hint}</Typography.Text>
                </Space>
            )}
        </Space>
    );
}

