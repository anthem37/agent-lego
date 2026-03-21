"use client";

import {PlusOutlined} from "@ant-design/icons";
import {
    Alert,
    Button,
    Cascader,
    Col,
    Divider,
    Drawer,
    Form,
    Input,
    message,
    Modal,
    Row,
    Select,
    Space,
    Spin,
    Typography,
} from "antd";
import React from "react";

import "@/components/kb/kb-quill-knowledge.css";

import {KbKnowledgeBodyEditor} from "@/components/kb/KbKnowledgeBodyEditor";
import type {KbRichTextFieldHandle} from "@/components/kb/KbRichTextField";
import {getKbDocument, ingestKbDocument, updateKbDocument} from "@/lib/kb/api";
import {formatKbToolFieldChipDisplay} from "@/lib/kb/kb-rich-tag-labels";
import type {KbDocumentDto} from "@/lib/kb/types";
import {kbToolRichTagLabel, kbToolSearchBlob} from "@/lib/kb/tool-labels";
import {getTool, listToolsPage} from "@/lib/tools/api";
import {isHttpParameterSchemaFormEditable, rowsFromOutputSchema} from "@/lib/tools/form";
import {resolveHttpOutputFieldDescription} from "@/lib/tools/http-output-field-description";
import {httpOutputRowsToCascaderOptions} from "@/lib/tools/tool-output-cascader";
import type {HttpParameterRow, ToolDto} from "@/lib/tools/types";
import {toolTypeDisplayName} from "@/lib/tool-labels";

const MAX_SIMILAR_QUERIES = 32;
const MAX_SIMILAR_QUERY_CHARS = 512;

export type IngestFormValues = {
    title: string;
    bodyRich?: string;
    /** 无 bodyRich 的旧文档编辑用 */
    bodyMarkdown?: string;
    similarQueries?: string[];
    linkedToolIds?: string[];
};

function kbRichEditorHasContent(html: string | undefined): boolean {
    const raw = (html ?? "").trim();
    if (!raw) {
        return false;
    }
    if (/\bkb-knowledge-inline\b|data-type=["']tool_field["']|data-type=["']tool["']/i.test(raw)) {
        return true;
    }
    const textOnly = raw.replace(/<[^>]*>/g, "").replace(/&nbsp;/gi, " ").trim();
    return textOnly.length > 0;
}

export type KbIngestDocumentDrawerProps = {
    open: boolean;
    onClose: () => void;
    collectionId: string | undefined;
    collectionName?: string;
    /** 传入则进入编辑模式，打开时拉取该文档 */
    editDocumentId?: string;
    onSuccess: (doc: KbDocumentDto) => void;
    onError?: (e: unknown) => void;
};

/**
 * 新增 / 编辑知识文档：单页滚动；绑定工具 + 富文本内嵌标签；tool_field 占位由服务端从 HTML 生成。
 */
export function KbIngestDocumentDrawer({
    open,
    onClose,
    collectionId,
    collectionName,
    editDocumentId,
    onSuccess,
    onError,
}: KbIngestDocumentDrawerProps) {
    const [form] = Form.useForm<IngestFormValues>();
    const [submitting, setSubmitting] = React.useState(false);
    const [toolCatalog, setToolCatalog] = React.useState<ToolDto[]>([]);
    const [loadingTools, setLoadingTools] = React.useState(false);
    const [fieldModalOpen, setFieldModalOpen] = React.useState(false);
    const [pickToolModalOpen, setPickToolModalOpen] = React.useState(false);
    const [pickToolId, setPickToolId] = React.useState<string | undefined>();
    const [fieldToolId, setFieldToolId] = React.useState<string | undefined>();
    const [fieldPathCascader, setFieldPathCascader] = React.useState<string[]>([]);
    const [fieldToolDetail, setFieldToolDetail] = React.useState<ToolDto | null>(null);
    const [loadingFieldTool, setLoadingFieldTool] = React.useState(false);
    const [loadingEditDoc, setLoadingEditDoc] = React.useState(false);
    const [legacyMarkdownMode, setLegacyMarkdownMode] = React.useState(false);
    const watchedLinkedToolIds = Form.useWatch("linkedToolIds", form);
    const richRef = React.useRef<KbRichTextFieldHandle | null>(null);

    const isEdit = Boolean(editDocumentId);

    const toolById = React.useMemo(() => {
        const m = new Map<string, ToolDto>();
        for (const t of toolCatalog) {
            m.set(t.id, t);
        }
        return m;
    }, [toolCatalog]);

    const toolSelectOptions = React.useMemo(
        () =>
            toolCatalog.map((t) => ({
                value: t.id,
                label: (
                    <div style={{lineHeight: 1.35}}>
                        <div>{kbToolRichTagLabel(t)}</div>
                        <Typography.Text type="secondary" style={{fontSize: 11}}>
                            {toolTypeDisplayName(t.toolType)}
                        </Typography.Text>
                    </div>
                ),
            })),
        [toolCatalog],
    );

    const linkedIds = React.useMemo(
        () => (watchedLinkedToolIds ?? []).filter(Boolean) as string[],
        [watchedLinkedToolIds],
    );

    const fieldModalToolOptions = React.useMemo(() => {
        return linkedIds.map((id) => {
            const t = toolById.get(id);
            return {
                value: id,
                label: t ? kbToolRichTagLabel(t) : id,
            };
        });
    }, [linkedIds, toolById]);

    React.useEffect(() => {
        if (!open) {
            return;
        }
        let cancelled = false;
        setLoadingTools(true);
        void listToolsPage({page: 1, pageSize: 200})
            .then((p) => {
                if (!cancelled) {
                    setToolCatalog(Array.isArray(p.items) ? p.items : []);
                }
            })
            .catch(() => {
                if (!cancelled) {
                    setToolCatalog([]);
                }
            })
            .finally(() => {
                if (!cancelled) {
                    setLoadingTools(false);
                }
            });
        return () => {
            cancelled = true;
        };
    }, [open]);

    React.useEffect(() => {
        if (!open || !collectionId) {
            return;
        }
        if (!editDocumentId) {
            setLegacyMarkdownMode(false);
            return;
        }
        let cancelled = false;
        setLoadingEditDoc(true);
        void getKbDocument(collectionId, editDocumentId)
            .then((d) => {
                if (cancelled) {
                    return;
                }
                const rich = d.bodyRich?.trim();
                if (rich) {
                    setLegacyMarkdownMode(false);
                    form.setFieldsValue({
                        title: d.title,
                        bodyRich: d.bodyRich,
                        bodyMarkdown: "",
                        linkedToolIds: d.linkedToolIds ?? [],
                        similarQueries: [],
                    });
                } else {
                    setLegacyMarkdownMode(true);
                    form.setFieldsValue({
                        title: d.title,
                        bodyRich: "",
                        bodyMarkdown: d.body ?? "",
                        linkedToolIds: d.linkedToolIds ?? [],
                        similarQueries: [],
                    });
                }
            })
            .catch((e) => {
                onError?.(e);
                message.error("加载文档失败");
                onClose();
            })
            .finally(() => {
                if (!cancelled) {
                    setLoadingEditDoc(false);
                }
            });
        return () => {
            cancelled = true;
        };
    }, [open, editDocumentId, collectionId, form, onClose, onError]);

    React.useEffect(() => {
        if (!fieldModalOpen) {
            return;
        }
        if (!fieldToolId && linkedIds.length > 0) {
            setFieldToolId(linkedIds[0]);
        }
    }, [fieldModalOpen, fieldToolId, linkedIds]);

    React.useEffect(() => {
        if (!fieldModalOpen || !fieldToolId) {
            setFieldToolDetail(null);
            return;
        }
        let cancelled = false;
        setLoadingFieldTool(true);
        void getTool(fieldToolId)
            .then((t) => {
                if (!cancelled) {
                    setFieldToolDetail(t);
                }
            })
            .catch(() => {
                if (!cancelled) {
                    setFieldToolDetail(toolById.get(fieldToolId) ?? null);
                }
            })
            .finally(() => {
                if (!cancelled) {
                    setLoadingFieldTool(false);
                }
            });
        return () => {
            cancelled = true;
        };
    }, [fieldModalOpen, fieldToolId, toolById]);

    const fieldToolOutputRows = React.useMemo((): HttpParameterRow[] => {
        const id = fieldToolId;
        if (!id) {
            return [];
        }
        const defRecord =
            (fieldToolDetail?.id === id ? fieldToolDetail.definition : undefined) ??
            toolCatalog.find((t) => t.id === id)?.definition;
        if (!defRecord || typeof defRecord !== "object") {
            return [];
        }
        const def = defRecord as Record<string, unknown>;
        const outSch = def.outputSchema;
        if (outSch === undefined || !isHttpParameterSchemaFormEditable(outSch)) {
            return [];
        }
        return rowsFromOutputSchema(def);
    }, [fieldToolId, fieldToolDetail, toolCatalog]);

    const fieldCascaderOptions = React.useMemo(
        () => httpOutputRowsToCascaderOptions(fieldToolOutputRows),
        [fieldToolOutputRows],
    );

    const handleSubmit = async (values: IngestFormValues) => {
        if (!collectionId) {
            message.warning("请先选择知识集合");
            return;
        }
        const hasRich = kbRichEditorHasContent(values.bodyRich);
        const mdTrim = (values.bodyMarkdown ?? "").trim();
        if (!hasRich && !mdTrim) {
            message.warning(
                legacyMarkdownMode ? "请填写 Markdown 正文" : "请填写正文，或使用上方工具栏「工具 / 字段」插入标签",
            );
            return;
        }
        if (!editDocumentId && !hasRich && mdTrim) {
            message.warning("新增文档请使用富文本编辑；仅 Markdown 路径用于编辑历史无富文本的文档。");
            return;
        }
        setSubmitting(true);
        try {
            const rawSq = values.similarQueries ?? [];
            const sqLines = rawSq
                .map((l) => (typeof l === "string" ? l.trim() : ""))
                .filter(Boolean)
                .slice(0, MAX_SIMILAR_QUERIES)
                .map((l) => (l.length > MAX_SIMILAR_QUERY_CHARS ? l.slice(0, MAX_SIMILAR_QUERY_CHARS) : l));
            const linked = (values.linkedToolIds ?? []).filter(Boolean);
            const richTrim = (values.bodyRich ?? "").trim();
            const payload = {
                title: values.title,
                ...(hasRich ? {bodyRich: richTrim} : {body: mdTrim}),
                ...(sqLines.length > 0 ? {similarQueries: sqLines} : {}),
                ...(linked.length > 0 ? {linkedToolIds: linked} : {}),
            };
            const doc = isEdit && editDocumentId
                ? await updateKbDocument(collectionId, editDocumentId, payload)
                : await ingestKbDocument(collectionId, payload);
            message.success(
                doc.status === "FAILED"
                    ? isEdit
                        ? "已保存但向量化失败，请查看列表中的错误信息"
                        : "文档已写入但向量化失败，请查看列表中的错误信息"
                    : isEdit
                      ? "已保存并完成向量化"
                      : "文档已写入并完成向量化",
            );
            form.resetFields();
            setLegacyMarkdownMode(false);
            onSuccess(doc);
            onClose();
        } catch (e) {
            onError?.(e);
        } finally {
            setSubmitting(false);
        }
    };

    const insertToolCode = (id: string) => {
        const t = toolById.get(id);
        const code = (t?.name?.trim() ? t.name.trim() : id) as string;
        const displayText = t ? kbToolRichTagLabel(t) : code;
        richRef.current?.insertToolTag(code, displayText);
    };

    const confirmInsertField = () => {
        const id = fieldToolId;
        if (!id) {
            message.warning("请选择工具");
            return;
        }
        if (fieldCascaderOptions.length === 0) {
            message.warning("该工具暂无可级联的出参结构，请先在工具管理配置 HTTP 出参（outputSchema）");
            return;
        }
        if (fieldPathCascader.length === 0) {
            message.warning("请通过级联选择器选出参路径");
            return;
        }
        const path = fieldPathCascader.join(".").replace(/^\$\.?/, "").replace(/^\$/, "");
        const t = toolById.get(id);
        const code = (t?.name?.trim() ? t.name.trim() : id) as string;
        const toolPart = t ? kbToolRichTagLabel(t) : code;
        const fieldDesc = resolveHttpOutputFieldDescription(fieldToolOutputRows, path);
        const displayText = formatKbToolFieldChipDisplay(toolPart, path, fieldDesc || undefined);
        richRef.current?.insertToolFieldTag(code, path, displayText, fieldDesc || undefined);
        setFieldModalOpen(false);
        setFieldPathCascader([]);
        message.success("已插入字段标签");
    };

    const confirmPickToolInsert = () => {
        const id = pickToolId;
        if (!id) {
            message.warning("请选择要插入的工具");
            return;
        }
        insertToolCode(id);
        setPickToolModalOpen(false);
        setPickToolId(undefined);
        message.success("已插入工具标签");
    };

    const linkedIdsKey = linkedIds.join("\u0001");

    const editorToolbarExtras = React.useMemo(
        () => ({
            onInsertTool: () => {
                if (linkedIds.length === 0) {
                    message.warning("请先在右侧「绑定工具」中选择至少一个工具");
                    return;
                }
                setPickToolId(linkedIds.length === 1 ? linkedIds[0] : undefined);
                setPickToolModalOpen(true);
            },
            onInsertField: () => {
                if (linkedIds.length === 0) {
                    message.warning("请先在右侧「绑定工具」中选择至少一个工具");
                    return;
                }
                setFieldPathCascader([]);
                setFieldModalOpen(true);
            },
            insertToolDisabled: linkedIds.length === 0,
            insertFieldDisabled: linkedIds.length === 0,
        }),
        // eslint-disable-next-line react-hooks/exhaustive-deps -- 仅随已绑定 id 集合变化
        [linkedIdsKey],
    );

    const drawerTitle = isEdit
        ? collectionName && collectionName.length > 0
            ? `编辑文档 · ${collectionName}`
            : "编辑文档"
        : collectionName && collectionName.length > 0
          ? `新增文档 · ${collectionName}`
          : "新增文档";

    return (
        <>
            <Drawer
                title={drawerTitle}
                width={1080}
                open={open}
                onClose={onClose}
                destroyOnHidden
                styles={{body: {paddingBottom: 96}}}
                afterOpenChange={(o) => {
                    if (o && !editDocumentId) {
                        form.resetFields();
                        setLegacyMarkdownMode(false);
                        setFieldPathCascader([]);
                        setFieldToolId(undefined);
                        setFieldToolDetail(null);
                        setPickToolId(undefined);
                    }
                }}
                extra={
                    <Space>
                        <Button onClick={onClose}>取消</Button>
                        <Button
                            type="primary"
                            loading={submitting || loadingEditDoc}
                            disabled={loadingEditDoc}
                            onClick={() => void form.submit()}
                        >
                            {isEdit ? "保存" : "提交入库"}
                        </Button>
                    </Space>
                }
            >
                <Spin spinning={loadingEditDoc}>
                <Alert
                    type="info"
                    showIcon
                    message="编写说明"
                    description={
                        <ul style={{margin: 0, paddingLeft: 18, fontSize: 13, lineHeight: 1.65}}>
                            <li>
                                在右侧 <strong>绑定工具</strong>，仅允许插入这些工具的标签（与工具管理列表一致）。
                            </li>
                            <li>
                                在正文中点击定位光标，使用工具栏右侧 <strong>图标按钮</strong>：插入工具引用，或插入出参字段（须级联选择路径）。
                            </li>
                            <li>
                                文中展示：工具标签为<strong>工具展示名</strong>（不显示英文 id）；字段标签为<strong>工具展示名.字段说明</strong>（与工具管理 HTTP 出参表「说明」列一致；未填说明时退回字段名）。悬停可看技术路径。删除标签请用退格/Delete 贴近标签操作。
                            </li>
                            <li>占位与向量化由服务端根据标签自动生成，无需另配映射表。</li>
                        </ul>
                    }
                    style={{marginBottom: 16}}
                />

                <Form
                    form={form}
                    layout="vertical"
                    onFinish={handleSubmit}
                    initialValues={{
                        bodyRich: "",
                        bodyMarkdown: "",
                        similarQueries: [],
                        linkedToolIds: [],
                    }}
                >
                    <Form.Item
                        name="title"
                        label="文档标题"
                        rules={[{required: true, message: "请输入标题"}]}
                    >
                        <Input size="large" placeholder="列表中显示的标题" allowClear/>
                    </Form.Item>

                    <Divider plain style={{margin: "12px 0 8px"}}>
                        <Typography.Text type="secondary">相似问（可选）</Typography.Text>
                    </Divider>
                    <Typography.Paragraph type="secondary" style={{fontSize: 12, marginTop: -4}}>
                        放在标题下方；口语化问法会拼入各分片向量文本以提升召回，最多 {MAX_SIMILAR_QUERIES} 条。
                    </Typography.Paragraph>
                    <Form.List name="similarQueries">
                        {(fields, {add, remove}) => (
                            <Space orientation="vertical" size={8} style={{width: "100%", marginBottom: 16}}>
                                {fields.map((field) => (
                                    <Space.Compact key={field.key} style={{width: "100%"}}>
                                        <Form.Item
                                            {...field}
                                            noStyle
                                            rules={[
                                                {
                                                    max: MAX_SIMILAR_QUERY_CHARS,
                                                    message: `单条不超过 ${MAX_SIMILAR_QUERY_CHARS} 字符`,
                                                },
                                            ]}
                                        >
                                            <Input
                                                placeholder="例如：怎么退款？"
                                                maxLength={MAX_SIMILAR_QUERY_CHARS}
                                                showCount
                                                style={{width: "calc(100% - 36px)"}}
                                            />
                                        </Form.Item>
                                        <Button type="text" danger onClick={() => remove(field.name)}>
                                            删
                                        </Button>
                                    </Space.Compact>
                                ))}
                                <Button
                                    type="dashed"
                                    icon={<PlusOutlined/>}
                                    onClick={() => add("")}
                                    disabled={fields.length >= MAX_SIMILAR_QUERIES}
                                    block
                                >
                                    添加一条（{fields.length}/{MAX_SIMILAR_QUERIES}）
                                </Button>
                            </Space>
                        )}
                    </Form.List>

                    <Row gutter={[20, 20]} align="top">
                        <Col xs={24} lg={16} flex="1 1 520px" style={{minWidth: 0}}>
                            <Typography.Text strong style={{display: "block", marginBottom: 8}}>
                                正文
                            </Typography.Text>
                            {legacyMarkdownMode ? (
                                <>
                                    <Alert
                                        type="warning"
                                        showIcon
                                        style={{marginBottom: 12}}
                                        message="该文档尚无富文本副本"
                                        description="当前以 Markdown 编辑；保存后按纯文本路径重新分片与向量化。若需工具/字段标签，可新建文档并仅使用富文本入库。"
                                    />
                                    <Form.Item
                                        name="bodyMarkdown"
                                        rules={[{required: true, message: "请输入 Markdown 正文"}]}
                                    >
                                        <Input.TextArea
                                            rows={18}
                                            placeholder="Markdown 正文"
                                            style={{fontFamily: "ui-monospace, monospace", fontSize: 13}}
                                        />
                                    </Form.Item>
                                </>
                            ) : (
                                <KbKnowledgeBodyEditor
                                    editorRef={richRef}
                                    minHeight={420}
                                    toolbarExtras={editorToolbarExtras}
                                />
                            )}
                        </Col>
                        <Col xs={24} lg={8} flex="0 0 280px" style={{minWidth: 260, maxWidth: 320}}>
                            <Typography.Text strong style={{display: "block", marginBottom: 4}}>
                                工具关联
                            </Typography.Text>
                            <Typography.Text type="secondary" style={{fontSize: 12, display: "block", marginBottom: 12}}>
                                列表与工具管理同源；正文中的「工具 / 字段」仅可插入此处绑定的工具。
                            </Typography.Text>
                            <Form.Item
                                name="linkedToolIds"
                                label="绑定工具"
                                style={{marginBottom: 8}}
                                extra="正文标签按运行时 name 写入 HTML。"
                            >
                                <Select
                                    mode="multiple"
                                    allowClear
                                    loading={loadingTools}
                                    options={toolSelectOptions}
                                    placeholder={loadingTools ? "加载工具中…" : "搜索并选择工具"}
                                    showSearch
                                    filterOption={(input, option) => {
                                        const q = input.trim().toLowerCase();
                                        if (!q) {
                                            return true;
                                        }
                                        const id = String(option?.value ?? "");
                                        const t = toolById.get(id);
                                        return t ? kbToolSearchBlob(t).includes(q) : false;
                                    }}
                                    popupMatchSelectWidth={520}
                                    maxTagCount="responsive"
                                />
                            </Form.Item>
                        </Col>
                    </Row>
                </Form>
                </Spin>
            </Drawer>

            <Modal
                title="插入工具标签"
                open={pickToolModalOpen}
                onOk={() => confirmPickToolInsert()}
                onCancel={() => {
                    setPickToolModalOpen(false);
                    setPickToolId(undefined);
                }}
                okText="插入"
                okButtonProps={{disabled: !pickToolId}}
                destroyOnHidden
            >
                <Typography.Paragraph type="secondary" style={{fontSize: 12, marginBottom: 8}}>
                    仅能选择右侧已绑定的工具；标签按该工具运行时 name 写入正文。
                </Typography.Paragraph>
                <Select
                    style={{width: "100%"}}
                    options={fieldModalToolOptions}
                    value={pickToolId}
                    onChange={setPickToolId}
                    placeholder="选择工具"
                    showSearch
                    filterOption={(input, option) => {
                        const q = input.trim().toLowerCase();
                        if (!q) {
                            return true;
                        }
                        return String(option?.label ?? "")
                            .toLowerCase()
                            .includes(q);
                    }}
                />
            </Modal>

            <Modal
                title="插入工具字段标签"
                open={fieldModalOpen}
                onOk={() => confirmInsertField()}
                onCancel={() => {
                    setFieldModalOpen(false);
                    setFieldPathCascader([]);
                }}
                okText="插入"
                okButtonProps={{
                    disabled:
                        loadingFieldTool ||
                        !fieldToolId ||
                        fieldCascaderOptions.length === 0 ||
                        fieldPathCascader.length === 0,
                }}
                destroyOnHidden
            >
                <Spin spinning={loadingFieldTool}>
                    <Space orientation="vertical" size={12} style={{width: "100%"}}>
                        <div>
                            <Typography.Text type="secondary" style={{fontSize: 12, display: "block", marginBottom: 4}}>
                                工具（与工具管理一致，打开时拉取最新 definition）
                            </Typography.Text>
                            <Select
                                style={{width: "100%"}}
                                options={fieldModalToolOptions}
                                value={fieldToolId}
                                onChange={(id) => {
                                    setFieldToolId(id);
                                    setFieldPathCascader([]);
                                }}
                                placeholder="选择已绑定工具"
                            />
                        </div>
                        {fieldCascaderOptions.length > 0 ? (
                            <div>
                                <Typography.Text type="secondary" style={{fontSize: 12, display: "block", marginBottom: 4}}>
                                    出参路径（须级联选择到底层字段，与工具管理 HTTP 出参表一致）
                                </Typography.Text>
                                <Cascader
                                    style={{width: "100%"}}
                                    options={fieldCascaderOptions}
                                    value={fieldPathCascader.length > 0 ? fieldPathCascader : undefined}
                                    onChange={(value) => {
                                        setFieldPathCascader((value as string[]) ?? []);
                                    }}
                                    placeholder="逐级展开并选择，如 data → orderNo"
                                    showSearch
                                />
                                {fieldPathCascader.length > 0 ? (
                                    <Typography.Paragraph type="secondary" style={{fontSize: 12, marginBottom: 0}}>
                                        文中展示：
                                        <Typography.Text strong>
                                            {formatKbToolFieldChipDisplay(
                                                (() => {
                                                    const ft = fieldToolId ? toolById.get(fieldToolId) : undefined;
                                                    return ft ? kbToolRichTagLabel(ft) : (fieldToolId ?? "");
                                                })(),
                                                fieldPathCascader
                                                    .join(".")
                                                    .replace(/^\$\.?/, "")
                                                    .replace(/^\$/, ""),
                                                resolveHttpOutputFieldDescription(
                                                    fieldToolOutputRows,
                                                    fieldPathCascader
                                                        .join(".")
                                                        .replace(/^\$\.?/, "")
                                                        .replace(/^\$/, ""),
                                                ) || undefined,
                                            )}
                                        </Typography.Text>
                                        <br/>
                                        <Typography.Text type="secondary" style={{fontSize: 11}}>
                                            技术路径（入库用）：<Typography.Text code>{fieldPathCascader.join(".")}</Typography.Text>
                                        </Typography.Text>
                                    </Typography.Paragraph>
                                ) : null}
                            </div>
                        ) : (
                            <Typography.Paragraph type="secondary" style={{fontSize: 12, marginBottom: 0}}>
                                {loadingFieldTool
                                    ? "正在加载工具详情…"
                                    : fieldToolId
                                      ? "当前工具没有可用的表格化 outputSchema（常见于未配置 HTTP 出参或非 HTTP 工具）。请先到「工具管理」为该工具配置出参结构后，再使用级联选择插入字段。"
                                      : "请先选择工具。"}
                            </Typography.Paragraph>
                        )}
                    </Space>
                </Spin>
            </Modal>
        </>
    );
}
