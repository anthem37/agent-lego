"use client";

import {PlusOutlined} from "@ant-design/icons";
import {
    Alert,
    Button,
    Cascader,
    Collapse,
    Drawer,
    Form,
    Input,
    message,
    Modal,
    Select,
    Space,
    Spin,
    Tag,
    Typography,
} from "antd";
import React from "react";

import "@/components/kb/kb-quill-knowledge.css";
import kbShell from "@/components/kb/kb-shell.module.css";

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

/** 后端仅允许 QUERY 类工具在正文中使用 tool_field / 出参字段嵌入。 */
function isQueryTool(t: ToolDto | undefined): boolean {
    return (t?.toolCategory ?? "ACTION").toUpperCase() === "QUERY";
}

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
                        <div>
                            <Space size={6} wrap>
                                <span>{kbToolRichTagLabel(t)}</span>
                                <Tag color={isQueryTool(t) ? "blue" : "default"} style={{marginInlineEnd: 0}}>
                                    {isQueryTool(t) ? "查询 QUERY" : "动作 ACTION"}
                                </Tag>
                            </Space>
                        </div>
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

    const linkedQueryToolIds = React.useMemo(
        () => linkedIds.filter((id) => isQueryTool(toolById.get(id))),
        [linkedIds, toolById],
    );

    /** 插入「工具」标签：允许所有已绑定工具。 */
    const pickToolSelectOptions = React.useMemo(() => {
        return linkedIds.map((id) => {
            const t = toolById.get(id);
            return {
                value: id,
                label: t ? kbToolRichTagLabel(t) : id,
            };
        });
    }, [linkedIds, toolById]);

    /** 插入「字段」标签：仅 QUERY 类已绑定工具（与后端校验一致）。 */
    const fieldModalToolOptions = React.useMemo(() => {
        return linkedQueryToolIds.map((id) => {
            const t = toolById.get(id);
            return {
                value: id,
                label: t ? kbToolRichTagLabel(t) : id,
            };
        });
    }, [linkedQueryToolIds, toolById]);

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
                /** 与 GET 一致：含空数组；勿用 length>0 否则无法回显「有数据」以外的合法状态 */
                const sq = Array.isArray(d.similarQueries) ? d.similarQueries : [];
                if (rich) {
                    setLegacyMarkdownMode(false);
                    form.setFieldsValue({
                        title: d.title,
                        bodyRich: d.bodyRich,
                        bodyMarkdown: "",
                        linkedToolIds: d.linkedToolIds ?? [],
                        similarQueries: sq,
                    });
                } else {
                    setLegacyMarkdownMode(true);
                    form.setFieldsValue({
                        title: d.title,
                        bodyRich: "",
                        bodyMarkdown: d.body ?? "",
                        linkedToolIds: d.linkedToolIds ?? [],
                        similarQueries: sq,
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
        if (linkedQueryToolIds.length === 0) {
            return;
        }
        if (!fieldToolId || !linkedQueryToolIds.includes(fieldToolId)) {
            setFieldToolId(linkedQueryToolIds[0]);
        }
    }, [fieldModalOpen, fieldToolId, linkedQueryToolIds]);

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
        const picked = toolById.get(id);
        if (!isQueryTool(picked)) {
            message.warning("出参字段嵌入仅支持「查询」类工具（QUERY），请重新选择或调整绑定。");
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
    const linkedQueryIdsKey = linkedQueryToolIds.join("\u0001");

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
                if (linkedQueryToolIds.length === 0) {
                    message.warning(
                        "出参字段嵌入仅支持「查询」类工具（QUERY）。请至少绑定一个查询类工具，或到工具管理将工具分类设为查询类。",
                    );
                    return;
                }
                setFieldPathCascader([]);
                setFieldModalOpen(true);
            },
            insertToolDisabled: linkedIds.length === 0,
            insertFieldDisabled: linkedQueryToolIds.length === 0,
        }),
        // eslint-disable-next-line react-hooks/exhaustive-deps -- 仅随已绑定 id / QUERY 子集变化
        [linkedIdsKey, linkedQueryIdsKey],
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
                size="min(1180px, calc(100vw - 24px))"
                open={open}
                onClose={onClose}
                destroyOnHidden
                styles={{
                    body: {padding: "20px 24px 96px"},
                    header: {padding: "16px 24px"},
                }}
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
                    <Space size={10}>
                        <Button onClick={onClose}>取消</Button>
                        <Button
                            type="primary"
                            size="large"
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
                    <Collapse
                        bordered={false}
                        className={kbShell.introCollapse}
                        defaultActiveKey={[]}
                        items={[
                            {
                                key: "intro",
                                label: (
                                    <span style={{fontWeight: 500, color: "rgba(0,0,0,0.75)"}}>
                                    编写与工具标签说明（建议首次阅读）
                                </span>
                                ),
                                children: (
                                    <ul className={kbShell.introList}>
                                        <li>
                                            在侧栏 <strong>绑定工具</strong>，仅允许插入这些工具的标签（与工具管理一致）。
                                        </li>
                                        <li>
                                            <strong>出参字段</strong>仅支持 <Tag color="blue">QUERY</Tag>；ACTION 仅可插入
                                            <strong>工具引用</strong>。
                                        </li>
                                        <li>
                                            正文中用工具栏图标插入工具或字段（字段需级联选出参路径）。
                                        </li>
                                        <li>
                                            展示名为工具展示名与字段说明；占位与向量化由服务端生成。
                                        </li>
                                    </ul>
                                ),
                            },
                        ]}
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
                            label={<span style={{fontWeight: 500}}>文档标题</span>}
                            rules={[{required: true, message: "请输入标题"}]}
                        >
                            <Input size="large" placeholder="在文档列表中显示的标题" allowClear/>
                        </Form.Item>

                        <Collapse
                            bordered={false}
                            className={kbShell.subtleCollapse}
                            defaultActiveKey={[]}
                            items={[
                                {
                                    key: "similar",
                                    label: (
                                        <Typography.Text type="secondary" style={{fontSize: 13}}>
                                            相似问（可选）— 用于提升召回，默认折叠
                                        </Typography.Text>
                                    ),
                                    children: (
                                        <>
                                            <Typography.Paragraph type="secondary" style={{fontSize: 12, marginTop: 0}}>
                                                会拼入<strong>每条分片</strong>的向量化文本与库的{" "}
                                                <Typography.Text code>embedding_text</Typography.Text>
                                                （与全文检索 GIN 索引），用语义接近用户真实提问的说法可提升召回。单条 ≤
                                                {MAX_SIMILAR_QUERY_CHARS} 字，最多 {MAX_SIMILAR_QUERIES} 条；与正文合计有长度上限时
                                                <strong>优先保留相似问块</strong>。
                                            </Typography.Paragraph>
                                            <Form.List name="similarQueries">
                                                {(fields, {add, remove}) => (
                                                    <Space orientation="vertical" size={8} style={{width: "100%"}}>
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
                                                                <Button type="text" danger
                                                                        onClick={() => remove(field.name)}>
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
                                        </>
                                    ),
                                },
                            ]}
                        />

                        <div className={kbShell.ingestSplit}>
                            <div className={kbShell.ingestMain}>
                                <span className={kbShell.sectionLabel}>正文</span>
                                {legacyMarkdownMode ? (
                                    <>
                                        <Alert
                                            type="warning"
                                            showIcon
                                            style={{marginBottom: 12, borderRadius: 10}}
                                            title="该文档尚无富文本副本"
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
                                        minHeight={440}
                                        toolbarExtras={editorToolbarExtras}
                                    />
                                )}
                            </div>
                            <aside className={kbShell.ingestAside}>
                                <span className={kbShell.asideTitle}>工具关联</span>
                                <span className={kbShell.asideHint}>
                                与工具管理同源；正文中的引用仅能来自此处绑定。移动端会先显示本区，便于先选工具再写正文。
                            </span>
                                <Alert
                                    type="info"
                                    showIcon
                                    className={kbShell.asideAlert}
                                    title="出参字段"
                                    description="仅 QUERY 查询类可嵌入字段；ACTION 仍可使用工具引用标签。"
                                />
                                <Form.Item
                                    name="linkedToolIds"
                                    label={<span style={{fontSize: 13}}>绑定工具</span>}
                                    style={{marginBottom: 0}}
                                    extra={
                                        <span style={{fontSize: 12}}>
                                        下拉标签区分 QUERY / ACTION；标签按运行时 name 写入 HTML。
                                    </span>
                                    }
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
                            </aside>
                        </div>
                    </Form>
                </Spin>
            </Drawer>

            <Modal
                title="插入工具标签"
                width={480}
                open={pickToolModalOpen}
                onOk={() => confirmPickToolInsert()}
                onCancel={() => {
                    setPickToolModalOpen(false);
                    setPickToolId(undefined);
                }}
                okText="插入"
                okButtonProps={{disabled: !pickToolId}}
                destroyOnHidden
                styles={{body: {paddingTop: 8}}}
            >
                <Typography.Paragraph type="secondary" style={{fontSize: 12, marginBottom: 8}}>
                    仅能选择右侧已绑定的工具；标签按该工具运行时 name 写入正文。
                </Typography.Paragraph>
                <Select
                    style={{width: "100%"}}
                    options={pickToolSelectOptions}
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
                width={560}
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
                styles={{body: {paddingTop: 8}}}
            >
                <Spin spinning={loadingFieldTool}>
                    <Space orientation="vertical" size={12} style={{width: "100%"}}>
                        <div>
                            <Typography.Text type="secondary" style={{fontSize: 12, display: "block", marginBottom: 4}}>
                                工具（仅列出已绑定且为 QUERY 查询类的工具，与后端校验一致；打开时拉取最新 definition）
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
                                <Typography.Text type="secondary"
                                                 style={{fontSize: 12, display: "block", marginBottom: 4}}>
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
                                            技术路径（入库用）：<Typography.Text
                                            code>{fieldPathCascader.join(".")}</Typography.Text>
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
