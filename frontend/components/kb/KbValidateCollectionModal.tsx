"use client";

import {AuditOutlined} from "@ant-design/icons";
import {Alert, Button, Checkbox, Modal, Space, Table, Tag, Typography} from "antd";
import React from "react";

import type {KbCollectionDocumentsValidationResponse} from "@/lib/kb/types";

export type KbValidateCollectionModalProps = {
    open: boolean;
    onClose: () => void;
    title: React.ReactNode;
    selectedCollectionId?: string;
    collectionValidateIncludeIssues: boolean;
    onCollectionValidateIncludeIssuesChange: (v: boolean) => void;
    collectionValidateLoading: boolean;
    onValidateEntireCollection: () => void;
    collectionValidateResult: KbCollectionDocumentsValidationResponse | null;
    onAfterOpenChange?: (open: boolean) => void;
};

export function KbValidateCollectionModal(props: KbValidateCollectionModalProps) {
    const {
        open,
        onClose,
        title,
        selectedCollectionId,
        collectionValidateIncludeIssues,
        onCollectionValidateIncludeIssuesChange,
        collectionValidateLoading,
        onValidateEntireCollection,
        collectionValidateResult,
        onAfterOpenChange,
    } = props;

    return (
        <Modal
            title={title}
            open={open}
            onCancel={onClose}
            footer={null}
            width={920}
            destroyOnHidden
            afterOpenChange={onAfterOpenChange}
        >
            {!selectedCollectionId ? (
                <Alert type="warning" showIcon title="请先在左侧选择一个知识集合"/>
            ) : (
                <Space orientation="vertical" size={12} style={{width: "100%"}}>
                    <Typography.Paragraph type="secondary" style={{fontSize: 12, marginBottom: 0}}>
                        对本集合内<strong>全部文档</strong>执行与单篇「校验」相同的规则（绑定、内联工具、tool_field
                        与 mappings 等），用于发布前批量门禁。
                    </Typography.Paragraph>
                    <Checkbox
                        checked={collectionValidateIncludeIssues}
                        onChange={(e) => onCollectionValidateIncludeIssuesChange(e.target.checked)}
                    >
                        返回每条完整 issues（文档很多时响应较大，适合深入排查）
                    </Checkbox>
                    <Button
                        type="primary"
                        icon={<AuditOutlined/>}
                        loading={collectionValidateLoading}
                        onClick={onValidateEntireCollection}
                    >
                        运行校验
                    </Button>
                    {collectionValidateResult ? (
                        <>
                            <Alert
                                type={
                                    (collectionValidateResult.documentsWithErrors ?? 0) > 0 ? "warning" : "success"
                                }
                                showIcon
                                title={
                                    <span>
                                        共 {collectionValidateResult.totalDocuments} 篇 · 无错误{" "}
                                        {collectionValidateResult.documentsOk} · 含错误{" "}
                                        {collectionValidateResult.documentsWithErrors} · 仅警告/提示{" "}
                                        {collectionValidateResult.documentsWithWarningsOnly}
                                    </span>
                                }
                            />
                            <Table
                                size="small"
                                rowKey={(r) => r.documentId}
                                dataSource={collectionValidateResult.items}
                                pagination={{pageSize: 8, showSizeChanger: true, pageSizeOptions: [8, 20, 50]}}
                                scroll={{x: 640}}
                                expandable={{
                                    expandedRowRender: (row) => {
                                        if (
                                            collectionValidateIncludeIssues &&
                                            row.issues &&
                                            row.issues.length > 0
                                        ) {
                                            return (
                                                <div style={{padding: "4px 0"}}>
                                                    {row.issues.map((issue, i) => (
                                                        <div
                                                            key={`${issue.code ?? issue.severity}-${i}`}
                                                            style={{display: "block", marginBottom: 6}}
                                                        >
                                                            <Tag
                                                                color={
                                                                    issue.severity === "ERROR"
                                                                        ? "red"
                                                                        : issue.severity === "WARN" ||
                                                                        issue.severity === "WARNING"
                                                                            ? "orange"
                                                                            : "blue"
                                                                }
                                                            >
                                                                {issue.severity}
                                                            </Tag>{" "}
                                                            {issue.code ? (
                                                                <Typography.Text code>{issue.code}</Typography.Text>
                                                            ) : null}{" "}
                                                            {issue.message ?? ""}
                                                        </div>
                                                    ))}
                                                </div>
                                            );
                                        }
                                        return (
                                            <Typography.Text type="secondary" style={{fontSize: 12}}>
                                                {collectionValidateIncludeIssues
                                                    ? "本条无 issues"
                                                    : "未勾选「返回完整 issues」；勾选后重新运行可展开详情"}
                                            </Typography.Text>
                                        );
                                    },
                                }}
                                columns={[
                                    {
                                        title: "标题",
                                        dataIndex: "title",
                                        ellipsis: true,
                                    },
                                    {
                                        title: "结果",
                                        key: "ok",
                                        width: 100,
                                        render: (_: unknown, r) =>
                                            r.ok ? <Tag color="success">通过</Tag> : <Tag color="error">有错误</Tag>,
                                    },
                                    {
                                        title: "错",
                                        dataIndex: "errorCount",
                                        width: 52,
                                    },
                                    {
                                        title: "警",
                                        dataIndex: "warnCount",
                                        width: 52,
                                    },
                                    {
                                        title: "信",
                                        dataIndex: "infoCount",
                                        width: 52,
                                    },
                                ]}
                            />
                        </>
                    ) : (
                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                            点击「运行校验」查看本集合全部文档结果。
                        </Typography.Text>
                    )}
                </Space>
            )}
        </Modal>
    );
}
