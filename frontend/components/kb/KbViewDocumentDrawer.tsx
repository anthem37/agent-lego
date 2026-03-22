"use client";

import {EditOutlined, ExperimentOutlined, SearchOutlined} from "@ant-design/icons";
import {
    Alert,
    Button,
    Checkbox,
    Drawer,
    Empty,
    Form,
    Input,
    InputNumber,
    message,
    Select,
    Space,
    Spin,
    Table,
    Tabs,
    Tag,
    Tooltip,
    Typography,
} from "antd";
import type {FormInstance} from "antd/es/form";
import React from "react";

import {DetailSection, SurfaceBox} from "@/components/ui";
import {DocRenderTestInputKeySelect, DocRenderTestOutputKeySelect} from "@/components/kb/DocRenderTestFormParts";
import {KbDocumentStatusTag} from "@/components/kb/KbDocumentStatusTag";
import {KbMarkdownPreview} from "@/components/kb/KbMarkdownPreview";
import {buildDocRenderInitialFromDocument, type DocRenderTestForm} from "@/lib/kb/doc-render-test";
import {buildKbRetrievePreviewColumns} from "@/lib/kb/retrieve-preview-table-columns";
import {kbToolRichTagLabel} from "@/lib/kb/tool-labels";
import type {KbDocumentDto, KbDocumentValidationResponse, KbRetrievePreviewHitDto} from "@/lib/kb/types";
import {getTool} from "@/lib/tools/api";
import type {ToolDto} from "@/lib/tools/types";

export type KbViewDocumentDrawerProps = {
    open: boolean;
    onClose: () => void;
    viewDocLoading: boolean;
    viewDocDetail: KbDocumentDto | null;
    selectedCollectionId?: string;
    viewToolById: Record<string, ToolDto>;
    loadingViewTools: boolean;
    viewDetailRichHtmlSafe: string;
    /** 关闭详情并打开入库抽屉 */
    onEditFromDrawer: () => void;
    docValidateLoading: boolean;
    docValidation: KbDocumentValidationResponse | null;
    onValidateCurrentDocument: () => void;
    docRetrieveQuery: string;
    setDocRetrieveQuery: (q: string) => void;
    docRetrieveTopK: number;
    setDocRetrieveTopK: (v: number) => void;
    docRetrieveTh: number;
    setDocRetrieveTh: (v: number) => void;
    docRetrieveRender: boolean;
    setDocRetrieveRender: (v: boolean) => void;
    docRetrieveLoading: boolean;
    docRetrieveHits: KbRetrievePreviewHitDto[] | null;
    onDocRetrievePreview: () => void;
    docRenderForm: FormInstance<DocRenderTestForm>;
    docRenderInitialValues: DocRenderTestForm;
    docRenderToolSelectOptions: { value: string; label: string }[];
    docRenderMergedToolById: Record<string, ToolDto>;
    onRenderTestToolLoaded: (tool: ToolDto) => void;
    docRenderLoading: boolean;
    docRenderedMd: string | null;
    onDocRenderPreview: () => void;
};

export function KbViewDocumentDrawer(props: KbViewDocumentDrawerProps) {
    const {
        open,
        onClose,
        viewDocLoading,
        viewDocDetail,
        selectedCollectionId,
        viewToolById,
        loadingViewTools,
        viewDetailRichHtmlSafe,
        onEditFromDrawer,
        docValidateLoading,
        docValidation,
        onValidateCurrentDocument,
        docRetrieveQuery,
        setDocRetrieveQuery,
        docRetrieveTopK,
        setDocRetrieveTopK,
        docRetrieveTh,
        setDocRetrieveTh,
        docRetrieveRender,
        setDocRetrieveRender,
        docRetrieveLoading,
        docRetrieveHits,
        onDocRetrievePreview,
        docRenderForm,
        docRenderInitialValues,
        docRenderToolSelectOptions,
        docRenderMergedToolById,
        onRenderTestToolLoaded,
        docRenderLoading,
        docRenderedMd,
        onDocRenderPreview,
    } = props;

    const docRetrieveColumns = React.useMemo(
        () =>
            buildKbRetrievePreviewColumns({
                onFillFirstQuery: setDocRetrieveQuery,
                renderedColumnWidth: 200,
            }),
        [setDocRetrieveQuery],
    );

    return (
        <Drawer
            title={viewDocDetail ? `文档：${viewDocDetail.title}` : "文档内容"}
            size={920}
            open={open}
            onClose={onClose}
            destroyOnHidden
            extra={
                viewDocDetail ? (
                    <Space wrap>
                        {viewDocDetail.body != null ? (
                            <Typography.Text copyable={{text: viewDocDetail.body}} type="secondary"
                                             style={{fontSize: 12}}>
                                复制原文
                            </Typography.Text>
                        ) : null}
                        <Button
                            type="primary"
                            size="small"
                            icon={<EditOutlined/>}
                            disabled={!selectedCollectionId}
                            onClick={onEditFromDrawer}
                        >
                            编辑
                        </Button>
                    </Space>
                ) : null
            }
        >
            <Spin spinning={viewDocLoading}>
                {viewDocDetail ? (
                    <Space orientation="vertical" size={12} style={{width: "100%"}}>
                        <Space wrap align="center">
                            <KbDocumentStatusTag status={viewDocDetail.status}/>
                            <Typography.Text type="secondary" style={{fontSize: 12}}>
                                ID {viewDocDetail.id}
                            </Typography.Text>
                        </Space>
                        {viewDocDetail.errorMessage ? (
                            <Alert type="error" title={viewDocDetail.errorMessage} showIcon/>
                        ) : null}
                        <DetailSection
                            title={`已绑定工具（${(viewDocDetail.linkedToolIds ?? []).length}）`}
                            extra={loadingViewTools ? <Spin size="small"/> : undefined}
                        >
                            {(viewDocDetail.linkedToolIds ?? []).length > 0 ? (
                                <Space wrap size={[6, 6]}>
                                    {viewDocDetail.linkedToolIds!.map((tid) => {
                                        const t = viewToolById[tid];
                                        const label = t ? kbToolRichTagLabel(t) : tid;
                                        return (
                                            <Tooltip key={tid} title={tid}>
                                                <Tag color="blue">{label}</Tag>
                                            </Tooltip>
                                        );
                                    })}
                                </Space>
                            ) : (
                                <Typography.Text type="secondary" style={{fontSize: 12}}>
                                    未绑定工具
                                </Typography.Text>
                            )}
                        </DetailSection>
                        <DetailSection title="相似问" hint="入库用于向量化与召回，最多 32 条">
                            {(viewDocDetail.similarQueries ?? []).length > 0 ? (
                                <Space wrap size={[6, 6]}>
                                    {(viewDocDetail.similarQueries ?? []).map((q, i) => (
                                        <Tag key={`sq-view-${i}-${q.slice(0, 32)}`}>
                                            {q.length > 96 ? `${q.slice(0, 94)}…` : q}
                                        </Tag>
                                    ))}
                                </Space>
                            ) : (
                                <Typography.Text type="secondary" style={{fontSize: 12}}>
                                    未配置
                                </Typography.Text>
                            )}
                        </DetailSection>
                        <Tabs
                            items={[
                                {
                                    key: "rich",
                                    label: "富文本",
                                    children: (
                                        <Space orientation="vertical" size={8} style={{width: "100%"}}>
                                            <Typography.Paragraph type="secondary"
                                                                  style={{fontSize: 12, marginBottom: 0}}>
                                                入库保存的 HTML
                                                与编辑器一致：工具标签仅<strong>工具展示名</strong>；字段标签为<strong>工具展示名.出参说明</strong>
                                                （完整路径见悬停）。打开详情时会按绑定工具与出参表刷新展示。
                                            </Typography.Paragraph>
                                            {viewDetailRichHtmlSafe ? (
                                                <SurfaceBox
                                                    className="kb-doc-rich-html"
                                                    style={{maxHeight: 480, overflow: "auto"}}
                                                    dangerouslySetInnerHTML={{__html: viewDetailRichHtmlSafe}}
                                                />
                                            ) : (
                                                <Typography.Text type="secondary">未入库富文本</Typography.Text>
                                            )}
                                        </Space>
                                    ),
                                },
                                {
                                    key: "raw",
                                    label: "原文（Markdown）",
                                    children: (
                                        <Space orientation="vertical" size={8} style={{width: "100%"}}>
                                            <Typography.Text type="secondary" style={{fontSize: 12}}>
                                                入库原文（Markdown，分块/召回依据）。其中的{" "}
                                                <Typography.Text code>{"{{tool:运行时name}}"}</Typography.Text>、
                                                <Typography.Text
                                                    code>{"{{tool_field:运行时name.出参路径}}"}</Typography.Text>{" "}
                                                为占位符：召回后可根据绑定执行工具，并将字段占位替换为真实出参值。
                                            </Typography.Text>
                                            <KbMarkdownPreview source={viewDocDetail.body ?? "_（无正文）_"}/>
                                        </Space>
                                    ),
                                },
                                {
                                    key: "validate",
                                    label: "校验",
                                    children: (
                                        <Space orientation="vertical" size={12} style={{width: "100%"}}>
                                            <Typography.Paragraph type="secondary"
                                                                  style={{fontSize: 12, marginBottom: 0}}>
                                                检查工具外链、正文内联工具提及、<Typography.Text
                                                code>tool_field</Typography.Text>{" "}
                                                与占位映射一致性等（与入库状态无关，失败/待处理文档也可运行）。
                                                批量校验整库请用顶部「整集合校验」。
                                            </Typography.Paragraph>
                                            <Button
                                                type="primary"
                                                icon={<ExperimentOutlined/>}
                                                loading={docValidateLoading}
                                                onClick={onValidateCurrentDocument}
                                            >
                                                运行校验
                                            </Button>
                                            {docValidation ? (
                                                <>
                                                    <Alert
                                                        type={docValidation.ok ? "success" : "warning"}
                                                        showIcon
                                                        title={
                                                            docValidation.ok
                                                                ? "校验通过"
                                                                : `共 ${docValidation.issues?.length ?? 0} 条提示`
                                                        }
                                                    />
                                                    <div
                                                        style={{
                                                            maxHeight: 360,
                                                            overflow: "auto",
                                                            border: "1px solid rgba(5, 5, 5, 0.06)",
                                                            borderRadius: 8,
                                                        }}
                                                    >
                                                        {(docValidation.issues ?? []).length === 0 ? (
                                                            <div
                                                                style={{
                                                                    padding: 16,
                                                                    textAlign: "center",
                                                                    color: "rgba(0,0,0,0.45)",
                                                                    fontSize: 13,
                                                                }}
                                                            >
                                                                无问题
                                                            </div>
                                                        ) : (
                                                            (docValidation.issues ?? []).map((issue, idx, arr) => (
                                                                <div
                                                                    key={`${issue.code ?? issue.severity}-${idx}`}
                                                                    style={{
                                                                        display: "block",
                                                                        padding: "10px 12px",
                                                                        borderBottom:
                                                                            idx < arr.length - 1
                                                                                ? "1px solid rgba(5,5,5,0.06)"
                                                                                : undefined,
                                                                    }}
                                                                >
                                                                    <Space wrap align="start">
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
                                                                        </Tag>
                                                                        {issue.code ? (
                                                                            <Typography.Text code copyable>
                                                                                {issue.code}
                                                                            </Typography.Text>
                                                                        ) : null}
                                                                    </Space>
                                                                    <div style={{marginTop: 4}}>
                                                                        <Typography.Text>{issue.message ?? "—"}</Typography.Text>
                                                                    </div>
                                                                </div>
                                                            ))
                                                        )}
                                                    </div>
                                                </>
                                            ) : (
                                                <Typography.Text type="secondary" style={{fontSize: 12}}>
                                                    点击「运行校验」查看结果。
                                                </Typography.Text>
                                            )}
                                        </Space>
                                    ),
                                },
                                {
                                    key: "retrieve",
                                    label: "召回测试",
                                    children: (
                                        <Space orientation="vertical" size={12} style={{width: "100%"}}>
                                            <Typography.Paragraph type="secondary"
                                                                  style={{fontSize: 12, marginBottom: 0}}>
                                                当前文档所在集合的单集合召回。若需<strong>多集合联合</strong>试查，请用顶部「召回调试」弹窗（与智能体多集合策略一致）。
                                            </Typography.Paragraph>
                                            <Input.TextArea
                                                rows={3}
                                                value={docRetrieveQuery}
                                                onChange={(e) => setDocRetrieveQuery(e.target.value)}
                                                placeholder="输入查询文本（可与入库时填写的「相似问」接近，便于验证召回）…"
                                            />
                                            <Space wrap align="center">
                                                <Typography.Text type="secondary" style={{fontSize: 12}}>
                                                    topK
                                                </Typography.Text>
                                                <InputNumber
                                                    min={1}
                                                    max={50}
                                                    value={docRetrieveTopK}
                                                    onChange={(v) => setDocRetrieveTopK(typeof v === "number" ? v : 5)}
                                                />
                                                <Typography.Text type="secondary" style={{fontSize: 12}}>
                                                    分数阈值
                                                </Typography.Text>
                                                <InputNumber
                                                    min={0}
                                                    max={1}
                                                    step={0.05}
                                                    value={docRetrieveTh}
                                                    onChange={(v) => setDocRetrieveTh(typeof v === "number" ? v : 0)}
                                                />
                                                <Checkbox
                                                    checked={docRetrieveRender}
                                                    onChange={(e) => setDocRetrieveRender(e.target.checked)}
                                                >
                                                    片段渲染
                                                </Checkbox>
                                                <Button
                                                    type="primary"
                                                    icon={<SearchOutlined/>}
                                                    loading={docRetrieveLoading}
                                                    onClick={onDocRetrievePreview}
                                                >
                                                    召回
                                                </Button>
                                            </Space>
                                            {docRetrieveHits != null ? (
                                                <Table<KbRetrievePreviewHitDto>
                                                    size="small"
                                                    rowKey={(r) => r.chunkId}
                                                    dataSource={docRetrieveHits}
                                                    pagination={{pageSize: 6, showSizeChanger: false}}
                                                    scroll={{x: 1180}}
                                                    columns={docRetrieveColumns}
                                                />
                                            ) : (
                                                <Typography.Text type="secondary" style={{fontSize: 12}}>
                                                    设置参数后点击「召回」查看命中列表。
                                                </Typography.Text>
                                            )}
                                        </Space>
                                    ),
                                },
                                {
                                    key: "renderTest",
                                    label: "渲染测试",
                                    children: (
                                        <Space orientation="vertical" size={12} style={{width: "100%"}}>
                                            <Typography.Paragraph type="secondary"
                                                                  style={{fontSize: 12, marginBottom: 0}}>
                                                用下拉选择<strong>工具</strong>、<strong>出参字段</strong>并填写
                                                mock（请求体{" "}
                                                <Typography.Text code>toolOutputs</Typography.Text>
                                                ，与会话一致）。出参选项来自<strong>占位符绑定</strong>及工具
                                                <Typography.Text code>outputSchema</Typography.Text>
                                                。下方<strong>入参</strong>仅对照工具
                                                parameters/schema，<strong>不参与当前渲染请求</strong>
                                                。工具变更时会按需拉取 <Typography.Text
                                                code>getTool</Typography.Text> 以补全定义。
                                            </Typography.Paragraph>
                                            <Button
                                                size="small"
                                                onClick={() => {
                                                    docRenderForm.setFieldsValue(buildDocRenderInitialFromDocument(viewDocDetail));
                                                    message.info("已按本文档绑定重置表单");
                                                }}
                                            >
                                                重置为本文档绑定
                                            </Button>
                                            <Form<DocRenderTestForm>
                                                form={docRenderForm}
                                                layout="vertical"
                                                key={viewDocDetail?.id ?? "doc-render"}
                                                initialValues={docRenderInitialValues}
                                                style={{width: "100%"}}
                                            >
                                                <Form.List name="toolBlocks">
                                                    {(blockFields, {add: addBlock, remove: removeBlock}) => (
                                                        <>
                                                            {blockFields.map((blockField) => (
                                                                <div
                                                                    key={blockField.key}
                                                                    style={{
                                                                        border: "1px solid var(--app-border)",
                                                                        borderRadius: 8,
                                                                        padding: 12,
                                                                        marginBottom: 12,
                                                                    }}
                                                                >
                                                                    <Space
                                                                        style={{
                                                                            width: "100%",
                                                                            justifyContent: "space-between",
                                                                        }}
                                                                    >
                                                                        <Typography.Text strong>
                                                                            工具出参 #{blockField.name + 1}
                                                                        </Typography.Text>
                                                                        {blockFields.length > 1 ? (
                                                                            <Button
                                                                                type="link"
                                                                                size="small"
                                                                                onClick={() => removeBlock(blockField.name)}
                                                                            >
                                                                                移除此工具
                                                                            </Button>
                                                                        ) : null}
                                                                    </Space>
                                                                    <Form.Item
                                                                        name={[blockField.name, "toolId"]}
                                                                        label="工具"
                                                                        rules={[{
                                                                            required: true,
                                                                            message: "请选择工具"
                                                                        }]}
                                                                    >
                                                                        <Select
                                                                            showSearch
                                                                            optionFilterProp="label"
                                                                            placeholder="选择工具"
                                                                            loading={loadingViewTools}
                                                                            options={docRenderToolSelectOptions}
                                                                            allowClear
                                                                            onChange={(tid) => {
                                                                                docRenderForm.setFieldValue(
                                                                                    ["toolBlocks", blockField.name, "pairs"],
                                                                                    [{key: "", value: ""}],
                                                                                );
                                                                                docRenderForm.setFieldValue(
                                                                                    ["toolBlocks", blockField.name, "inputPairs"],
                                                                                    [],
                                                                                );
                                                                                if (tid) {
                                                                                    void getTool(String(tid)).then((t) => {
                                                                                        onRenderTestToolLoaded(t);
                                                                                    });
                                                                                }
                                                                            }}
                                                                        />
                                                                    </Form.Item>
                                                                    <Typography.Paragraph
                                                                        type="secondary"
                                                                        style={{fontSize: 12, marginBottom: 8}}
                                                                    >
                                                                        出参 mock：先选字段再填值；嵌套路径由下拉自动带出。
                                                                    </Typography.Paragraph>
                                                                    <Form.List name={[blockField.name, "pairs"]}>
                                                                        {(pairFields, {add: addPair, remove}) => (
                                                                            <>
                                                                                {pairFields.map((pairField) => (
                                                                                    <Space
                                                                                        key={pairField.key}
                                                                                        align="baseline"
                                                                                        style={{
                                                                                            display: "flex",
                                                                                            marginBottom: 8,
                                                                                            flexWrap: "wrap",
                                                                                        }}
                                                                                    >
                                                                                        <DocRenderTestOutputKeySelect
                                                                                            form={docRenderForm}
                                                                                            blockIndex={blockField.name}
                                                                                            pairFieldName={pairField.name}
                                                                                            doc={viewDocDetail}
                                                                                            toolById={docRenderMergedToolById}
                                                                                        />
                                                                                        <Form.Item
                                                                                            name={[pairField.name, "value"]}
                                                                                            noStyle
                                                                                        >
                                                                                            <Input placeholder="值"
                                                                                                   style={{minWidth: 160}}/>
                                                                                        </Form.Item>
                                                                                        <Button
                                                                                            type="link"
                                                                                            onClick={() => remove(pairField.name)}
                                                                                        >
                                                                                            删除
                                                                                        </Button>
                                                                                    </Space>
                                                                                ))}
                                                                                <Button
                                                                                    type="dashed"
                                                                                    size="small"
                                                                                    onClick={() =>
                                                                                        addPair({key: "", value: ""})
                                                                                    }
                                                                                    block
                                                                                >
                                                                                    添加出参字段
                                                                                </Button>
                                                                            </>
                                                                        )}
                                                                    </Form.List>
                                                                    <Typography.Paragraph
                                                                        type="secondary"
                                                                        style={{
                                                                            fontSize: 12,
                                                                            marginBottom: 8,
                                                                            marginTop: 12,
                                                                        }}
                                                                    >
                                                                        入参 mock（可选）：字段来自工具 definition 的
                                                                        parameters / inputSchema，
                                                                        仅本地对照，不参与「渲染预览」请求。
                                                                    </Typography.Paragraph>
                                                                    <Form.List name={[blockField.name, "inputPairs"]}>
                                                                        {(
                                                                            inputPairFields,
                                                                            {
                                                                                add: addInputPair,
                                                                                remove: removeInputPair
                                                                            },
                                                                        ) => (
                                                                            <>
                                                                                {inputPairFields.map((ipField) => (
                                                                                    <Space
                                                                                        key={ipField.key}
                                                                                        align="baseline"
                                                                                        style={{
                                                                                            display: "flex",
                                                                                            marginBottom: 8,
                                                                                            flexWrap: "wrap",
                                                                                        }}
                                                                                    >
                                                                                        <DocRenderTestInputKeySelect
                                                                                            form={docRenderForm}
                                                                                            blockIndex={blockField.name}
                                                                                            pairFieldName={ipField.name}
                                                                                            toolById={docRenderMergedToolById}
                                                                                        />
                                                                                        <Form.Item
                                                                                            name={[ipField.name, "value"]}
                                                                                            noStyle
                                                                                        >
                                                                                            <Input
                                                                                                placeholder="mock 值"
                                                                                                style={{minWidth: 160}}
                                                                                            />
                                                                                        </Form.Item>
                                                                                        <Button
                                                                                            type="link"
                                                                                            onClick={() =>
                                                                                                removeInputPair(ipField.name)
                                                                                            }
                                                                                        >
                                                                                            删除
                                                                                        </Button>
                                                                                    </Space>
                                                                                ))}
                                                                                <Button
                                                                                    type="dashed"
                                                                                    size="small"
                                                                                    onClick={() =>
                                                                                        addInputPair({
                                                                                            key: "",
                                                                                            value: ""
                                                                                        })
                                                                                    }
                                                                                    block
                                                                                >
                                                                                    添加入参字段
                                                                                </Button>
                                                                            </>
                                                                        )}
                                                                    </Form.List>
                                                                </div>
                                                            ))}
                                                            <Button
                                                                type="dashed"
                                                                onClick={() =>
                                                                    addBlock({
                                                                        toolId: "",
                                                                        pairs: [{key: "", value: ""}],
                                                                        inputPairs: [],
                                                                    })
                                                                }
                                                                block
                                                            >
                                                                添加另一个工具
                                                            </Button>
                                                        </>
                                                    )}
                                                </Form.List>
                                            </Form>
                                            <Button type="primary" loading={docRenderLoading}
                                                    onClick={onDocRenderPreview}>
                                                渲染预览
                                            </Button>
                                            {docRenderedMd != null ? (
                                                <SurfaceBox style={{maxHeight: 420, overflow: "auto"}}>
                                                    <KbMarkdownPreview source={docRenderedMd || "_（空正文）_"}/>
                                                </SurfaceBox>
                                            ) : (
                                                <Typography.Text type="secondary" style={{fontSize: 12}}>
                                                    选择工具与出参字段并填写 mock 后点击「渲染预览」。
                                                </Typography.Text>
                                            )}
                                        </Space>
                                    ),
                                },
                            ]}
                        />
                    </Space>
                ) : !viewDocLoading ? (
                    <Empty description="暂无数据"/>
                ) : null}
            </Spin>
        </Drawer>
    );
}
