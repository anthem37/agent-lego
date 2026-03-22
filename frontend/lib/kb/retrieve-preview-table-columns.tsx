"use client";

import {Tooltip} from "antd";
import type {ColumnsType} from "antd/es/table";

import {KbSimilarQueriesHitCell} from "@/components/kb/KbSimilarQueriesHitCell";
import type {KbRetrievePreviewHitDto} from "@/lib/kb/types";

export type KbRetrievePreviewColumnOptions = {
    /** 点击「用首条填查询框」时写入父级查询输入 */
    onFillFirstQuery: (q: string) => void;
    /** 「渲染后」列宽，单文档抽屉与多集合弹窗可略有不同 */
    renderedColumnWidth?: number;
};

export function buildKbRetrievePreviewColumns(
    opts: KbRetrievePreviewColumnOptions,
): ColumnsType<KbRetrievePreviewHitDto> {
    const {onFillFirstQuery, renderedColumnWidth = 200} = opts;
    return [
        {
            title: "分数",
            dataIndex: "score",
            width: 88,
            render: (s: number) => (typeof s === "number" ? s.toFixed(4) : s),
        },
        {
            title: "集合",
            key: "coll",
            width: 120,
            ellipsis: true,
            render: (_: unknown, r) => r.collectionName ?? r.collectionId ?? "—",
        },
        {
            title: "文档",
            key: "doc",
            width: 140,
            ellipsis: true,
            render: (_: unknown, r) => r.documentTitle ?? r.documentId,
        },
        {
            title: "相似问",
            key: "similarQueries",
            width: 240,
            render: (_: unknown, r) => (
                <KbSimilarQueriesHitCell row={r} onFillFirst={onFillFirstQuery}/>
            ),
        },
        {
            title: "片段",
            dataIndex: "content",
            ellipsis: true,
            render: (t: string) => (
                <Tooltip title={t}>
                    <span>{t}</span>
                </Tooltip>
            ),
        },
        {
            title: "渲染后",
            dataIndex: "renderedContent",
            width: renderedColumnWidth,
            ellipsis: true,
            render: (t: string | null | undefined) =>
                t ? (
                    <Tooltip title={t}>
                        <span>{t}</span>
                    </Tooltip>
                ) : (
                    "—"
                ),
        },
    ];
}
