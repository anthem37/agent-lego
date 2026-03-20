"use client";

import {Space, Typography} from "antd";
import React from "react";

export function PageHeaderBlock(props: {
    title: string;
    subtitle?: React.ReactNode;
    extra?: React.ReactNode;
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
                    alignItems: "center",
                    justifyContent: "space-between",
                    gap: 12,
                    flexWrap: "wrap",
                }}
            >
                <Space orientation="vertical" size={4}>
                    <Typography.Title level={3} style={{margin: 0}}>
                        {props.title}
                    </Typography.Title>
                    {props.subtitle ? <Typography.Text type="secondary">{props.subtitle}</Typography.Text> : null}
                </Space>
                {props.extra ? <div>{props.extra}</div> : null}
            </div>
        </div>
    );
}

