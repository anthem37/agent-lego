"use client";

import {Space, Typography} from "antd";
import React from "react";

export function PageHeaderBlock(props: {
    title: string;
    subtitle?: React.ReactNode;
    extra?: React.ReactNode;
    /** 页面级业务图标（如知识库书本、模型云主机等），置于标题左侧 */
    icon?: React.ReactNode;
}) {
    return (
        <div
            style={{
                background: "white",
                border: "1px solid #edf1f7",
                borderRadius: 12,
                padding: 16,
            }}
        >
            <div
                style={{
                    display: "flex",
                    alignItems: "flex-start",
                    justifyContent: "space-between",
                    gap: 12,
                    flexWrap: "wrap",
                }}
            >
                <Space align="start" size={14} style={{flex: 1, minWidth: 240}}>
                    {props.icon ? (
                        <div
                            style={{
                                width: 48,
                                height: 48,
                                borderRadius: 12,
                                background: "linear-gradient(135deg, rgba(22,119,255,0.12), rgba(114,46,209,0.14))",
                                border: "1px solid rgba(22,119,255,0.2)",
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "center",
                                fontSize: 22,
                                color: "#1677ff",
                                flexShrink: 0,
                            }}
                        >
                            {props.icon}
                        </div>
                    ) : null}
                    <Space orientation="vertical" size={4} style={{minWidth: 0}}>
                        <Typography.Title level={3} style={{margin: 0}}>
                            {props.title}
                        </Typography.Title>
                        {props.subtitle ? (
                            <Typography.Text type="secondary" style={{display: "block", maxWidth: 720}}>
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

