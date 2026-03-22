"use client";

import {AuditOutlined, FileAddOutlined, SearchOutlined} from "@ant-design/icons";
import {Alert, Button, Col, Collapse, Empty, Row, Space, Statistic, Table, Tooltip, Typography,} from "antd";
import React from "react";

import type {KbChunkStrategyMetaDto, KbCollectionDto, KbDocumentDto} from "@/lib/kb/types";
import {chunkStrategyLabel, maskVectorStoreConfig} from "@/lib/kb/page-helpers";
import {tablePaginationFriendly} from "@/lib/table-pagination";
import type {ColumnsType} from "antd/es/table";

import kbShell from "@/components/kb/kb-shell.module.css";

export type KbDocumentsMainPanelProps = {
    chunkMeta: KbChunkStrategyMetaDto[];
    collections: KbCollectionDto[];
    selectedCollection: KbCollectionDto | undefined;
    selectedCollectionId?: string;
    documents: KbDocumentDto[];
    loadingDocs: boolean;
    docColumns: ColumnsType<KbDocumentDto>;
    onOpenRetrieveModal: () => void;
    onOpenValidateCollectionModal: () => void;
    onOpenIngest: () => void;
};

export function KbDocumentsMainPanel(props: KbDocumentsMainPanelProps) {
    const {
        chunkMeta,
        collections,
        selectedCollection,
        selectedCollectionId,
        documents,
        loadingDocs,
        docColumns,
        onOpenRetrieveModal,
        onOpenValidateCollectionModal,
        onOpenIngest,
    } = props;

    return (
        <div className={kbShell.mainPanel}>
            <div className={kbShell.mainHeader}>
                <div style={{minWidth: 0}}>
                    <Typography.Title level={5} className={kbShell.mainTitle}>
                        {selectedCollection ? selectedCollection.name : "文档与内容"}
                    </Typography.Title>
                    <span className={kbShell.mainSubtitle}>
                        {selectedCollection
                            ? `当前集合 · ${documents.length} 篇 · 分片 ${chunkStrategyLabel(chunkMeta, selectedCollection.chunkStrategy)}`
                            : "请从左侧选择一个集合"}
                    </span>
                </div>
                <div className={kbShell.mainActions}>
                    <Space wrap size={10}>
                        <Space.Compact>
                            <Button
                                icon={<SearchOutlined/>}
                                disabled={collections.length === 0}
                                title="按查询预览向量召回"
                                onClick={onOpenRetrieveModal}
                            >
                                召回调试
                            </Button>
                            <Button
                                icon={<AuditOutlined/>}
                                disabled={!selectedCollectionId}
                                title="校验文档与工具占位（含 QUERY 出参字段等）"
                                onClick={onOpenValidateCollectionModal}
                            >
                                整集合校验
                            </Button>
                        </Space.Compact>
                        <Button
                            type="primary"
                            icon={<FileAddOutlined/>}
                            disabled={!selectedCollectionId}
                            onClick={onOpenIngest}
                        >
                            新增文档
                        </Button>
                    </Space>
                </div>
            </div>

            {selectedCollection ? (
                <div className={kbShell.mainBody}>
                    <div className={kbShell.tableWrap}>
                        <Table<KbDocumentDto>
                            size="middle"
                            rowKey="id"
                            loading={loadingDocs}
                            dataSource={documents}
                            pagination={tablePaginationFriendly()}
                            columns={docColumns}
                            scroll={{x: 860}}
                        />
                    </div>
                    <Collapse
                        bordered={false}
                        className={kbShell.metaCollapse}
                        items={[
                            {
                                key: "collection-meta",
                                label: "集合详情与向量配置",
                                children: (
                                    <Space orientation="vertical" size={12} style={{width: "100%"}}>
                                        <Alert
                                            type="info"
                                            showIcon
                                            title="写入说明"
                                            description="提交后服务端会分片、调用嵌入模型并写入向量库；大正文请控制在平台配置的字节/分片上限内。失败时文档仍会落库，状态为失败并附带错误原因。知识正文中若使用工具出参字段嵌入，仅允许绑定「查询 QUERY」类工具（与入库校验一致）。"
                                        />
                                        {selectedCollection.vectorStoreConfig &&
                                        Object.keys(selectedCollection.vectorStoreConfig).length > 0 ? (
                                            <Alert
                                                type="warning"
                                                showIcon
                                                title="向量库配置（敏感字段已打码）"
                                                description={
                                                    <Typography.Text
                                                        code
                                                        copyable
                                                        style={{fontSize: 12, whiteSpace: "pre-wrap"}}
                                                    >
                                                        {JSON.stringify(
                                                            maskVectorStoreConfig(selectedCollection.vectorStoreConfig),
                                                            null,
                                                            2,
                                                        )}
                                                    </Typography.Text>
                                                }
                                            />
                                        ) : null}
                                        {selectedCollection.chunkParams &&
                                        Object.keys(selectedCollection.chunkParams).length > 0 ? (
                                            <Alert
                                                type="success"
                                                showIcon
                                                title="当前集合分片参数"
                                                description={
                                                    <Typography.Text
                                                        code
                                                        copyable
                                                        style={{fontSize: 12, whiteSpace: "pre-wrap"}}
                                                    >
                                                        {JSON.stringify(selectedCollection.chunkParams, null, 2)}
                                                    </Typography.Text>
                                                }
                                            />
                                        ) : null}
                                        <Row gutter={16}>
                                            <Col span={8}>
                                                <Statistic title="集合内文档数" value={documents.length}/>
                                            </Col>
                                            <Col span={8}>
                                                <Statistic
                                                    title="向量维度"
                                                    value={selectedCollection.embeddingDims ?? "—"}
                                                />
                                            </Col>
                                            <Col span={8}>
                                                <Statistic
                                                    title="嵌入模型 ID"
                                                    valueRender={() => (
                                                        <Tooltip title={selectedCollection.embeddingModelId}>
                                                            <Typography.Text
                                                                ellipsis
                                                                style={{
                                                                    maxWidth: "100%",
                                                                    display: "block",
                                                                }}
                                                                copyable={{
                                                                    text: selectedCollection.embeddingModelId,
                                                                }}
                                                            >
                                                                {selectedCollection.embeddingModelId}
                                                            </Typography.Text>
                                                        </Tooltip>
                                                    )}
                                                />
                                            </Col>
                                        </Row>
                                        {selectedCollection.vectorStoreProfileId ? (
                                            <Typography.Paragraph
                                                type="secondary"
                                                style={{marginBottom: 0, marginTop: 8}}
                                            >
                                                公共向量库 profile：{" "}
                                                <Typography.Text
                                                    code
                                                    copyable={{
                                                        text: selectedCollection.vectorStoreProfileId,
                                                    }}
                                                >
                                                    {selectedCollection.vectorStoreProfileId}
                                                </Typography.Text>
                                            </Typography.Paragraph>
                                        ) : null}
                                    </Space>
                                ),
                            },
                        ]}
                    />
                </div>
            ) : (
                <div className={kbShell.emptyState}>
                    <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description={
                            <Space orientation="vertical" size={8}>
                                <Typography.Text>未选择知识集合</Typography.Text>
                                <Typography.Text type="secondary">
                                    在左侧列表中点击集合名称，即可查看文档、写入语料并维护向量索引。
                                </Typography.Text>
                            </Space>
                        }
                    />
                </div>
            )}
        </div>
    );
}
