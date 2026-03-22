"use client";

import {ArrowLeftOutlined} from "@ant-design/icons";
import {Button, Space, Typography} from "antd";
import Link from "next/link";
import React from "react";

export function PageHeaderBlock(props: {
    title: string;
    subtitle?: React.ReactNode;
    extra?: React.ReactNode;
    /** 页面级业务图标（如知识库书本、模型云主机等），置于标题左侧 */
    icon?: React.ReactNode;
    /** 返回列表等上一级入口 */
    backHref?: string;
    backLabel?: string;
}) {
    return (
        <div
            style={{
                background: "var(--app-surface)",
                border: "1px solid var(--app-border)",
                borderRadius: "var(--app-radius-lg)",
                padding: "18px 20px",
                boxShadow: "var(--app-shadow-sm)",
            }}
        >
            {props.backHref ? (
                <div style={{marginBottom: 10}}>
                    <Link href={props.backHref}>
                        <Button type="link" icon={<ArrowLeftOutlined/>} style={{paddingInline: 0, height: "auto"}}>
                            {props.backLabel ?? "返回列表"}
                        </Button>
                    </Link>
                </div>
            ) : null}
            <div
                style={{
                    display: "flex",
                    alignItems: "flex-start",
                    justifyContent: "space-between",
                    gap: 16,
                    flexWrap: "wrap",
                }}
            >
                <Space align="start" size={14} style={{flex: 1, minWidth: 240}}>
                    {props.icon ? (
                        <div
                            style={{
                                width: 48,
                                height: 48,
                                borderRadius: "var(--app-radius-md)",
                                background:
                                    "linear-gradient(145deg, var(--app-primary-softer), var(--app-primary-soft))",
                                border: "1px solid var(--app-primary-ring, rgba(22, 119, 255, 0.22))",
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "center",
                                fontSize: 22,
                                color: "var(--app-primary)",
                                flexShrink: 0,
                            }}
                        >
                            {props.icon}
                        </div>
                    ) : null}
                    <Space orientation="vertical" size={6} style={{minWidth: 0}}>
                        <Typography.Title level={3} style={{margin: 0, fontWeight: 600}}>
                            {props.title}
                        </Typography.Title>
                        {props.subtitle ? (
                            <Typography.Text
                                type="secondary"
                                style={{display: "block", maxWidth: 800, lineHeight: 1.6}}
                            >
                                {props.subtitle}
                            </Typography.Text>
                        ) : null}
                    </Space>
                </Space>
                {props.extra ? <div style={{flexShrink: 0}}>{props.extra}</div> : null}
            </div>
        </div>
    );
}
