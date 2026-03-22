"use client";

import {DeleteOutlined, PlusOutlined, SearchOutlined} from "@ant-design/icons";
import {Button, Empty, Input, Popconfirm, Space, Spin, Tag, Typography} from "antd";
import React from "react";

import type {KbChunkStrategyMetaDto, KbCollectionDto} from "@/lib/kb/types";
import {chunkStrategyLabel} from "@/lib/kb/page-helpers";

import kbShell from "@/components/kb/kb-shell.module.css";

export type KbCollectionListPanelProps = {
    chunkMeta: KbChunkStrategyMetaDto[];
    collectionQuery: string;
    onCollectionQueryChange: (q: string) => void;
    filteredCollections: KbCollectionDto[];
    loadingCollections: boolean;
    selectedCollectionId?: string;
    onSelectCollection: (id: string) => void;
    deletingCollectionId: string | null;
    onDeleteCollection: (collectionId: string) => void | Promise<void>;
    onOpenCreateModal: () => void;
};

export function KbCollectionListPanel(props: KbCollectionListPanelProps) {
    const {
        chunkMeta,
        collectionQuery,
        onCollectionQueryChange,
        filteredCollections,
        loadingCollections,
        selectedCollectionId,
        onSelectCollection,
        deletingCollectionId,
        onDeleteCollection,
        onOpenCreateModal,
    } = props;

    return (
        <div className={kbShell.shellPanel}>
            <div className={kbShell.shellHeader}>
                <div style={{minWidth: 0, flex: 1}}>
                    <div className={kbShell.shellHeaderTitle}>知识集合</div>
                    <p className={kbShell.shellHeaderHint}>点选后在右侧管理文档与向量索引</p>
                </div>
                <Button type="primary" size="small" icon={<PlusOutlined/>} onClick={onOpenCreateModal}>
                    新建
                </Button>
            </div>
            <div className={kbShell.shellSearch}>
                <Input
                    allowClear
                    size="middle"
                    prefix={<SearchOutlined style={{color: "var(--app-text-muted)"}}/>}
                    placeholder="搜索名称、ID、模型…"
                    value={collectionQuery}
                    onChange={(e) => onCollectionQueryChange(e.target.value)}
                />
            </div>
            <div className={kbShell.shellListScroll}>
                {loadingCollections ? (
                    <div style={{padding: 32, textAlign: "center"}}>
                        <Spin/>
                    </div>
                ) : filteredCollections.length === 0 ? (
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无集合">
                        <Button type="primary" size="small" onClick={onOpenCreateModal}>
                            创建第一个集合
                        </Button>
                    </Empty>
                ) : (
                    filteredCollections.map((item) => {
                        const active = item.id === selectedCollectionId;
                        return (
                            <div
                                key={item.id}
                                className={`${kbShell.collectionItem} ${active ? kbShell.collectionItemActive : ""}`}
                                onClick={() => onSelectCollection(item.id)}
                                role="presentation"
                                style={{
                                    display: "flex",
                                    alignItems: "flex-start",
                                    justifyContent: "space-between",
                                    gap: 12,
                                }}
                            >
                                <div style={{minWidth: 0, flex: 1}}>
                                    <Typography.Text
                                        ellipsis
                                        style={{maxWidth: "100%", display: "block", marginBottom: 4}}
                                    >
                                        {item.name}
                                    </Typography.Text>
                                    <Space orientation="vertical" size={2} style={{width: "100%"}}>
                                        <Typography.Text type="secondary" style={{fontSize: 12}} ellipsis>
                                            ID {item.id}
                                        </Typography.Text>
                                        <Space size={4} wrap>
                                            <Tag color="blue">{item.embeddingDims ?? "—"} 维</Tag>
                                            {item.vectorStoreKind ? (
                                                <Tag color="purple">{item.vectorStoreKind}</Tag>
                                            ) : null}
                                            {item.chunkStrategy ? (
                                                <Tag color="geekblue">
                                                    {chunkStrategyLabel(chunkMeta, item.chunkStrategy)}
                                                </Tag>
                                            ) : null}
                                        </Space>
                                    </Space>
                                </div>
                                <div onClick={(e) => e.stopPropagation()} role="presentation">
                                    <Popconfirm
                                        title="删除整个集合？"
                                        description="删除 PostgreSQL 中文档与集合元数据，并从智能体策略中移除引用；随后尝试删除对应 Milvus 物理 collection。"
                                        okText="删除"
                                        cancelText="取消"
                                        okButtonProps={{danger: true}}
                                        onConfirm={(e) => {
                                            e?.stopPropagation();
                                            void onDeleteCollection(item.id);
                                        }}
                                    >
                                        <Button
                                            type="text"
                                            danger
                                            size="small"
                                            icon={<DeleteOutlined/>}
                                            loading={deletingCollectionId === item.id}
                                            onClick={(e) => e.stopPropagation()}
                                        />
                                    </Popconfirm>
                                </div>
                            </div>
                        );
                    })
                )}
            </div>
        </div>
    );
}
