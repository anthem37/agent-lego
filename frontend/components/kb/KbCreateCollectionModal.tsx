"use client";

import {Alert, Button, Divider, Form, Input, InputNumber, Modal, Select, Space, Tag, Typography,} from "antd";
import type {FormInstance} from "antd/es/form";
import React from "react";

import type {KbChunkStrategyMetaDto} from "@/lib/kb/types";
import {collectionNamePatternForKind} from "@/lib/kb/page-helpers";
import type {VectorStoreProfileDto} from "@/lib/vector-store/types";

export type CreateCollectionForm = {
    name: string;
    description?: string;
    vectorStoreProfileId: string;
    collectionNameOverride?: string;
    chunkStrategy: string;
    maxChars: number;
    overlap: number;
    headingLevel?: number;
    leadMaxChars?: number;
};

export type KbCreateCollectionModalProps = {
    open: boolean;
    onCancel: () => void;
    onFinish: (values: CreateCollectionForm) => void | Promise<void>;
    form: FormInstance<CreateCollectionForm>;
    creating: boolean;
    vectorProfiles: VectorStoreProfileDto[];
    chunkMeta: KbChunkStrategyMetaDto[];
    watchedProfileForCreate: VectorStoreProfileDto | undefined;
    applyChunkStrategyDefaults: (strategy: string) => void;
};

export function KbCreateCollectionModal(props: KbCreateCollectionModalProps) {
    const {
        open,
        onCancel,
        onFinish,
        form,
        creating,
        vectorProfiles,
        chunkMeta,
        watchedProfileForCreate,
        applyChunkStrategyDefaults,
    } = props;

    return (
        <Modal
            title="新建知识集合"
            open={open}
            onCancel={onCancel}
            footer={null}
            destroyOnHidden
            width={640}
            afterOpenChange={(isOpen) => {
                if (!isOpen) {
                    form.resetFields();
                    return;
                }
                const first = chunkMeta[0];
                const fixed = chunkMeta.find((m) => m.value === "FIXED_WINDOW") ?? first;
                const p = fixed?.defaultParams ?? {};
                form.setFieldsValue({
                    chunkStrategy: fixed?.value ?? "FIXED_WINDOW",
                    maxChars: typeof p.maxChars === "number" ? p.maxChars : 900,
                    overlap: typeof p.overlap === "number" ? p.overlap : 120,
                    headingLevel: typeof p.headingLevel === "number" ? p.headingLevel : 2,
                    leadMaxChars: typeof p.leadMaxChars === "number" ? p.leadMaxChars : 512,
                    vectorStoreProfileId: undefined,
                    collectionNameOverride: "",
                });
            }}
        >
            {vectorProfiles.length === 0 ? (
                <Alert
                    type="warning"
                    showIcon
                    style={{marginBottom: 16}}
                    title="暂无公共向量库"
                    description="请先在「向量库配置」中创建公共 profile（连接与嵌入模型）；物理 collection 名可在本页覆盖填写。远程集合运维见「向量库集合」。"
                />
            ) : null}
            <Form
                form={form}
                layout="vertical"
                preserve
                onFinish={onFinish}
                initialValues={{
                    chunkStrategy: "FIXED_WINDOW",
                    maxChars: 900,
                    overlap: 120,
                    headingLevel: 2,
                    leadMaxChars: 512,
                    collectionNameOverride: "",
                }}
            >
                <Form.Item name="name" label="集合名称" rules={[{required: true, message: "请输入名称"}]}>
                    <Input placeholder="例如 产品说明、内部制度"/>
                </Form.Item>
                <Form.Item name="description" label="描述（可选）">
                    <Input placeholder="便于他人理解该集合用途"/>
                </Form.Item>
                <Form.Item
                    name="chunkStrategy"
                    label="分片策略"
                    rules={[{required: true, message: "请选择分片策略"}]}
                    extra="创建后不可修改；不同策略影响检索粒度与召回行为。"
                >
                    <Select
                        placeholder="选择分片策略"
                        options={chunkMeta.map((m) => ({
                            label: m.label,
                            value: m.value,
                            title: m.description,
                        }))}
                        onChange={(v) => applyChunkStrategyDefaults(String(v))}
                    />
                </Form.Item>
                <Form.Item shouldUpdate={(prev, cur) => prev.chunkStrategy !== cur.chunkStrategy} noStyle>
                    {() => {
                        const s = form.getFieldValue("chunkStrategy") as string | undefined;
                        const m = chunkMeta.find((x) => x.value === s);
                        return m ? (
                            <Typography.Paragraph type="secondary" style={{marginBottom: 12, marginTop: -4}}>
                                {m.description}
                            </Typography.Paragraph>
                        ) : null;
                    }}
                </Form.Item>
                <Form.Item
                    label="maxChars"
                    required
                    extra="单条分片最大字符数，范围 128～8192（与嵌入模型上下文相关）。"
                >
                    <Space align="center" wrap>
                        <Form.Item
                            name="maxChars"
                            noStyle
                            rules={[
                                {required: true, message: "请输入 maxChars"},
                                {
                                    type: "number",
                                    min: 128,
                                    max: 8192,
                                    message: "须在 128～8192 之间",
                                },
                            ]}
                        >
                            <InputNumber min={128} max={8192} style={{width: 140}}/>
                        </Form.Item>
                        <Typography.Text type="secondary">overlap</Typography.Text>
                        <Form.Item
                            name="overlap"
                            noStyle
                            rules={[
                                {required: true, message: "请输入 overlap"},
                                {type: "number", min: 0, message: "不能为负"},
                            ]}
                        >
                            <InputNumber min={0} max={4096} style={{width: 120}}/>
                        </Form.Item>
                    </Space>
                </Form.Item>
                <Form.Item noStyle shouldUpdate>
                    {() =>
                        form.getFieldValue("chunkStrategy") === "HEADING_SECTION" ? (
                            <Space align="start" wrap style={{width: "100%", marginBottom: 12}}>
                                <Form.Item
                                    name="headingLevel"
                                    label="标题级别"
                                    extra="1=按 # 切节；2=按 # 再按 ## 切小节。"
                                    rules={[
                                        {required: true, message: "请选择标题级别"},
                                        {
                                            type: "number",
                                            min: 1,
                                            max: 2,
                                            message: "仅支持 1 或 2",
                                        },
                                    ]}
                                >
                                    <InputNumber min={1} max={2} style={{width: 120}}/>
                                </Form.Item>
                                <Form.Item
                                    name="leadMaxChars"
                                    label="引导段 maxChars"
                                    extra="用于向量拼接的引导正文长度，64～8192。"
                                    rules={[
                                        {required: true, message: "请输入 leadMaxChars"},
                                        {
                                            type: "number",
                                            min: 64,
                                            max: 8192,
                                            message: "须在 64～8192 之间",
                                        },
                                    ]}
                                >
                                    <InputNumber min={64} max={8192} style={{width: 140}}/>
                                </Form.Item>
                            </Space>
                        ) : null
                    }
                </Form.Item>
                <Divider plain style={{margin: "16px 0 12px"}}>
                    公共向量库
                </Divider>
                <Typography.Paragraph type="secondary" style={{marginTop: -8, marginBottom: 12}}>
                    知识库须引用「向量库配置」中的公共 profile；连接仅在配置页维护，远程 collection 运维在「向量库」页。每个
                    profile 下每个<strong>物理 collection</strong>至多绑定<strong>一个</strong>知识库集合。
                </Typography.Paragraph>
                <Form.Item
                    name="vectorStoreProfileId"
                    label="公共向量库 profile"
                    rules={[{required: true, message: "请选择公共向量库"}]}
                    extra="嵌入模型与维度由所选 profile 决定；物理 collection 名须在 profile 中配置，或于下方覆盖。"
                >
                    <Select
                        showSearch
                        allowClear
                        placeholder="选择已配置的公共向量库"
                        options={vectorProfiles.map((p) => ({
                            label: `${p.name}（${p.vectorStoreKind} · ${p.embeddingModelId}）`,
                            value: p.id,
                        }))}
                        optionFilterProp="label"
                    />
                </Form.Item>
                {watchedProfileForCreate ? (
                    <Alert
                        type="info"
                        showIcon
                        style={{marginBottom: 12}}
                        title={
                            <span>
                                已选：{watchedProfileForCreate.name}{" "}
                                <Tag color="purple">{watchedProfileForCreate.vectorStoreKind}</Tag>
                            </span>
                        }
                        description={
                            <Space orientation="vertical" size={4} style={{width: "100%"}}>
                                <Typography.Text type="secondary" style={{fontSize: 12}}>
                                    嵌入模型：{watchedProfileForCreate.embeddingModelId}，维度{" "}
                                    {watchedProfileForCreate.embeddingDims}
                                </Typography.Text>
                            </Space>
                        }
                    />
                ) : null}
                <Form.Item
                    name="collectionNameOverride"
                    label="覆盖物理 collection 名（可选）"
                    rules={[
                        {
                            validator: async (_, v) => {
                                const s = typeof v === "string" ? v.trim() : "";
                                if (!s) {
                                    return;
                                }
                                const pat = collectionNamePatternForKind(watchedProfileForCreate?.vectorStoreKind);
                                if (!pat.test(s)) {
                                    throw new Error(
                                        watchedProfileForCreate?.vectorStoreKind?.toUpperCase() === "QDRANT"
                                            ? "Qdrant：仅允许字母、数字、下划线与连字符"
                                            : "Milvus：仅允许字母、数字与下划线",
                                    );
                                }
                            },
                        },
                    ]}
                    extra="留空则使用公共 profile 配置中的 collectionName。"
                >
                    <Input placeholder="例如 kb_product_docs（与向量库类型命名规则一致）"/>
                </Form.Item>
                <Form.Item style={{marginBottom: 0, textAlign: "right"}}>
                    <Space>
                        <Button onClick={onCancel}>取消</Button>
                        <Button type="primary" htmlType="submit" loading={creating}>
                            创建
                        </Button>
                    </Space>
                </Form.Item>
            </Form>
        </Modal>
    );
}
