"use client";

import {SearchOutlined} from "@ant-design/icons";
import {Alert, Button, Checkbox, Input, InputNumber, message, Modal, Select, Space, Table, Typography,} from "antd";
import React from "react";

import {buildKbRetrievePreviewColumns} from "@/lib/kb/retrieve-preview-table-columns";
import type {KbAgentPolicySummaryDto, KbCollectionDto, KbRetrievePreviewHitDto,} from "@/lib/kb/types";

export type KbMultiCollectionRetrieveModalProps = {
    open: boolean;
    onClose: () => void;
    /** 与 antd Modal afterOpenChange 一致：关闭时清空 hits，打开时可 bump 智能体下拉 key */
    onModalOpenChange?: (open: boolean) => void;
    agentPolicySelectNonce: number;
    collections: KbCollectionDto[];
    collRetrieveOptionsCollections: KbCollectionDto[];
    collRetrieveCollectionIds: string[];
    onCollRetrieveCollectionIdsChange: (ids: string[]) => void;
    collRetrieveQuery: string;
    onCollRetrieveQueryChange: (q: string) => void;
    collRetrieveTopK: number;
    onCollRetrieveTopKChange: (v: number) => void;
    collRetrieveTh: number;
    onCollRetrieveThChange: (v: number) => void;
    collRetrieveRender: boolean;
    onCollRetrieveRenderChange: (v: boolean) => void;
    collRetrieveLoading: boolean;
    collRetrieveHits: KbRetrievePreviewHitDto[] | null;
    onCollRetrievePreview: () => void;
    agentKbSummaries: KbAgentPolicySummaryDto[];
    agentKbSummariesLoading: boolean;
};

export function KbMultiCollectionRetrieveModal(props: KbMultiCollectionRetrieveModalProps) {
    const {
        open,
        onClose,
        onModalOpenChange,
        agentPolicySelectNonce,
        collections,
        collRetrieveOptionsCollections,
        collRetrieveCollectionIds,
        onCollRetrieveCollectionIdsChange,
        collRetrieveQuery,
        onCollRetrieveQueryChange,
        collRetrieveTopK,
        onCollRetrieveTopKChange,
        collRetrieveTh,
        onCollRetrieveThChange,
        collRetrieveRender,
        onCollRetrieveRenderChange,
        collRetrieveLoading,
        collRetrieveHits,
        onCollRetrievePreview,
        agentKbSummaries,
        agentKbSummariesLoading,
    } = props;

    const retrievePreviewColumns = React.useMemo(
        () =>
            buildKbRetrievePreviewColumns({
                onFillFirstQuery: onCollRetrieveQueryChange,
                renderedColumnWidth: 180,
            }),
        [onCollRetrieveQueryChange],
    );

    return (
        <Modal
            title="多集合召回调试"
            open={open}
            onCancel={onClose}
            footer={null}
            width={840}
            destroyOnHidden
            afterOpenChange={(isOpen) => onModalOpenChange?.(isOpen)}
        >
            {collections.length === 0 ? (
                <Alert type="warning" showIcon title="暂无知识集合，请先创建"/>
            ) : (
                <Space orientation="vertical" size={12} style={{width: "100%"}}>
                    <Typography.Paragraph type="secondary" style={{fontSize: 12, marginBottom: 0}}>
                        选择一个或多个集合联合检索，逻辑与智能体{" "}
                        <Typography.Text code>knowledge_base_policy.collectionIds</Typography.Text>{" "}
                        一致；所选集合须使用<strong>相同嵌入模型</strong>。融合后的 topK 条为全局排序结果。
                    </Typography.Paragraph>
                    <div>
                        <Typography.Text type="secondary" style={{fontSize: 12, display: "block", marginBottom: 6}}>
                            从智能体加载（与线上 RAG 范围对齐）
                        </Typography.Text>
                        <Select
                            key={agentPolicySelectNonce}
                            showSearch
                            allowClear
                            placeholder="选择已配置 collectionIds 的智能体，一键填入下方召回范围"
                            style={{width: "100%"}}
                            loading={agentKbSummariesLoading}
                            options={agentKbSummaries.map((a) => {
                                const n = (a.collectionIds ?? []).length;
                                return {
                                    value: a.agentId,
                                    label: `${a.agentName || a.agentId}（${n} 个集合）`,
                                    disabled: n === 0,
                                };
                            })}
                            optionFilterProp="label"
                            onChange={(agentId) => {
                                if (agentId == null || agentId === "") {
                                    return;
                                }
                                const row = agentKbSummaries.find((x) => x.agentId === agentId);
                                const ids = [...(row?.collectionIds ?? [])];
                                if (ids.length === 0) {
                                    message.warning("该智能体未配置 collectionIds");
                                    return;
                                }
                                onCollRetrieveCollectionIdsChange(ids);
                                message.success(`已填入 ${ids.length} 个集合`);
                            }}
                        />
                    </div>
                    <div>
                        <Typography.Text type="secondary" style={{fontSize: 12, display: "block", marginBottom: 6}}>
                            召回范围
                        </Typography.Text>
                        <Select
                            mode="multiple"
                            allowClear
                            placeholder="选择 1 个或多个集合（须同一嵌入模型）"
                            style={{width: "100%"}}
                            options={collRetrieveOptionsCollections.map((c) => ({
                                label: `${c.name} · ${c.embeddingDims ?? "—"}维`,
                                value: c.id,
                            }))}
                            value={collRetrieveCollectionIds}
                            onChange={(ids) => onCollRetrieveCollectionIdsChange(ids as string[])}
                            maxTagCount="responsive"
                        />
                    </div>
                    {collRetrieveCollectionIds.length > 0 &&
                    collRetrieveOptionsCollections.length < collections.length ? (
                        <Alert
                            type="info"
                            showIcon
                            title="已按首个选中集合的嵌入模型过滤可选集合；与后端多集合 RAG 约束一致。"
                        />
                    ) : null}
                    <Input.TextArea
                        rows={3}
                        value={collRetrieveQuery}
                        onChange={(e) => onCollRetrieveQueryChange(e.target.value)}
                        placeholder="输入查询文本…"
                    />
                    <Space wrap align="center">
                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                            topK
                        </Typography.Text>
                        <InputNumber
                            min={1}
                            max={50}
                            value={collRetrieveTopK}
                            onChange={(v) => onCollRetrieveTopKChange(typeof v === "number" ? v : 5)}
                        />
                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                            分数阈值
                        </Typography.Text>
                        <InputNumber
                            min={0}
                            max={1}
                            step={0.05}
                            value={collRetrieveTh}
                            onChange={(v) => onCollRetrieveThChange(typeof v === "number" ? v : 0)}
                        />
                        <Checkbox
                            checked={collRetrieveRender}
                            onChange={(e) => onCollRetrieveRenderChange(e.target.checked)}
                        >
                            片段渲染
                        </Checkbox>
                        <Button
                            type="primary"
                            icon={<SearchOutlined/>}
                            loading={collRetrieveLoading}
                            onClick={onCollRetrievePreview}
                        >
                            召回
                        </Button>
                    </Space>
                    {collRetrieveHits != null ? (
                        <Table<KbRetrievePreviewHitDto>
                            size="small"
                            rowKey={(r) => r.chunkId}
                            dataSource={collRetrieveHits}
                            pagination={{pageSize: 8, showSizeChanger: false}}
                            scroll={{x: 1180}}
                            columns={retrievePreviewColumns}
                        />
                    ) : (
                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                            选择集合并输入查询后点击「召回」。
                        </Typography.Text>
                    )}
                </Space>
            )}
        </Modal>
    );
}
