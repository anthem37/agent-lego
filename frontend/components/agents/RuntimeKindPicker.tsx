"use client";

import {Card, Col, Row, Space, Tag, Typography} from "antd";
import React from "react";

import {AGENT_RUNTIME, AGENT_RUNTIME_OPTIONS, type AgentRuntimeKind} from "@/lib/agents/runtime-kinds";

export type RuntimeKindPickerProps = {
    value?: AgentRuntimeKind;
    onChange?: (v: AgentRuntimeKind) => void;
};

export function RuntimeKindPicker({value, onChange}: RuntimeKindPickerProps) {
    return (
        <Row gutter={[16, 16]}>
            {AGENT_RUNTIME_OPTIONS.map((opt) => (
                <Col xs={24} md={12} key={opt.value}>
                    <Card
                        size="small"
                        hoverable
                        onClick={() => onChange?.(opt.value)}
                        style={{
                            borderColor:
                                value === opt.value
                                    ? "var(--app-primary, #1677ff)"
                                    : "var(--app-border)",
                            boxShadow:
                                value === opt.value
                                    ? "0 0 0 1px rgba(22,119,255,0.35)"
                                    : undefined,
                            cursor: "pointer",
                            height: "100%",
                        }}
                    >
                        <Space orientation="vertical" size={8} style={{width: "100%"}}>
                            <Space wrap>
                                <Typography.Text strong>{opt.title}</Typography.Text>
                                <Tag color={opt.value === AGENT_RUNTIME.REACT ? "blue" : "default"}>
                                    {opt.badge}
                                </Tag>
                            </Space>
                            <Typography.Paragraph
                                type="secondary"
                                style={{marginBottom: 8, fontSize: 13}}
                            >
                                {opt.summary}
                            </Typography.Paragraph>
                            <ul style={{margin: 0, paddingLeft: 18, fontSize: 12, lineHeight: 1.55}}>
                                {opt.bullets.map((b) => (
                                    <li key={b}>{b}</li>
                                ))}
                            </ul>
                        </Space>
                    </Card>
                </Col>
            ))}
        </Row>
    );
}
