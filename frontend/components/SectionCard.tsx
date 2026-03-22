"use client";

import {Card} from "antd";
import React from "react";

export function SectionCard(props: {
    title: React.ReactNode;
    extra?: React.ReactNode;
    children: React.ReactNode;
    loading?: boolean;
}) {
    return (
        <Card
            title={props.title}
            extra={props.extra}
            loading={props.loading}
            style={{
                borderRadius: "var(--app-radius-lg)",
                borderColor: "var(--app-border)",
                boxShadow: "var(--app-shadow-sm)",
                background: "var(--app-surface)",
            }}
            styles={{
                header: {
                    minHeight: 52,
                    fontWeight: 600,
                },
            }}
        >
            {props.children}
        </Card>
    );
}
