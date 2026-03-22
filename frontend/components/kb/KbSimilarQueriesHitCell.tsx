"use client";

import {Button, Space, Tag, Typography} from "antd";
import React from "react";

import type {KbRetrievePreviewHitDto} from "@/lib/kb/types";

export type KbSimilarQueriesHitCellProps = {
    row: KbRetrievePreviewHitDto;
    onFillFirst: (q: string) => void;
};

export function KbSimilarQueriesHitCell(props: KbSimilarQueriesHitCellProps) {
    const sq = props.row.similarQueries ?? [];
    if (sq.length === 0) {
        return <Typography.Text type="secondary">—</Typography.Text>;
    }
    return (
        <Space orientation="vertical" size={4} style={{maxWidth: 280}}>
            <Space wrap size={[4, 4]}>
                {sq.map((s, i) => (
                    <Tag key={`${i}-${s.slice(0, 24)}`}>{s.length > 56 ? `${s.slice(0, 54)}…` : s}</Tag>
                ))}
            </Space>
            <Button
                type="link"
                size="small"
                style={{padding: 0, height: "auto"}}
                onClick={() => props.onFillFirst(sq[0])}
            >
                用首条填查询框
            </Button>
        </Space>
    );
}
