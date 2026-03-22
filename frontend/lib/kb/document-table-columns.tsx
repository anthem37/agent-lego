"use client";

import {DeleteOutlined, EditOutlined, EyeOutlined} from "@ant-design/icons";
import {Button, Popconfirm, Space, Tag, Tooltip, Typography} from "antd";
import type {ColumnsType} from "antd/es/table";
import React from "react";

import {KbDocumentStatusTag} from "@/components/kb/KbDocumentStatusTag";
import {formatKbDateTime} from "@/lib/kb/page-helpers";
import type {KbDocumentDto} from "@/lib/kb/types";

export type KbDocumentTableColumnOptions = {
    selectedCollectionId?: string;
    deletingDocumentId: string | null;
    onViewDocument: (documentId: string) => void | Promise<void>;
    onEditIngest: (documentId: string) => void;
    onDeleteDocument: (documentId: string) => void | Promise<void>;
};

export function buildKbDocumentTableColumns(opts: KbDocumentTableColumnOptions): ColumnsType<KbDocumentDto> {
    const {selectedCollectionId, deletingDocumentId, onViewDocument, onEditIngest, onDeleteDocument} = opts;
    return [
        {
            title: "标题",
            dataIndex: "title",
            ellipsis: true,
            render: (t: string, row: KbDocumentDto) => (
                <Tooltip title={row.id}>
                    <Typography.Text strong>{t}</Typography.Text>
                </Tooltip>
            ),
        },
        {
            title: "状态",
            dataIndex: "status",
            width: 100,
            render: (s: string) => <KbDocumentStatusTag status={s}/>,
        },
        {
            title: "工具",
            key: "tools",
            width: 72,
            render: (_: unknown, row: KbDocumentDto) => <Tag>{(row.linkedToolIds ?? []).length}</Tag>,
        },
        {
            title: "错误信息",
            dataIndex: "errorMessage",
            ellipsis: true,
            render: (t: string | undefined) =>
                t ? (
                    <Typography.Text type="danger" style={{fontSize: 13}}>
                        {t}
                    </Typography.Text>
                ) : (
                    "—"
                ),
        },
        {
            title: "创建时间",
            dataIndex: "createdAt",
            width: 156,
            render: (v: string | undefined) => formatKbDateTime(v),
        },
        {
            title: "操作",
            key: "actions",
            width: 196,
            fixed: "right",
            render: (_: unknown, row: KbDocumentDto) => (
                <Space size={0} wrap={false}>
                    <Button
                        type="link"
                        size="small"
                        icon={<EyeOutlined/>}
                        disabled={!selectedCollectionId}
                        onClick={() => void onViewDocument(row.id)}
                    >
                        查看
                    </Button>
                    <Button
                        type="link"
                        size="small"
                        icon={<EditOutlined/>}
                        disabled={!selectedCollectionId}
                        onClick={() => onEditIngest(row.id)}
                    >
                        编辑
                    </Button>
                    <Popconfirm
                        title="删除该文档？"
                        description="将删除文档及其向量分片，不可恢复。"
                        okText="删除"
                        cancelText="取消"
                        okButtonProps={{danger: true}}
                        onConfirm={() => void onDeleteDocument(row.id)}
                    >
                        <Button
                            type="link"
                            danger
                            size="small"
                            icon={<DeleteOutlined/>}
                            disabled={!selectedCollectionId}
                            loading={deletingDocumentId === row.id}
                        />
                    </Popconfirm>
                </Space>
            ),
        },
    ];
}
