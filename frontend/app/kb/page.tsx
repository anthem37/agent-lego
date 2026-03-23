"use client";

import {BookOutlined, ReloadOutlined} from "@ant-design/icons";
import {Button, Col, Form, message, Row,} from "antd";
import dynamic from "next/dynamic";
import React from "react";

import {isAbortError} from "@/lib/api/isAbortError";
import {DEFAULT_REQUEST_TIMEOUT_MS} from "@/lib/api/request";
import {AppLayout} from "@/components/AppLayout";
import {PageShell} from "@/components/PageShell";
import {ErrorAlert} from "@/components/ErrorAlert";
import {PageHeaderBlock} from "@/components/PageHeaderBlock";
import {KbCollectionListPanel} from "@/components/kb/KbCollectionListPanel";
import {type CreateCollectionForm, KbCreateCollectionModal} from "@/components/kb/KbCreateCollectionModal";
import {KbDocumentsMainPanel} from "@/components/kb/KbDocumentsMainPanel";
import {KbMultiCollectionRetrieveModal} from "@/components/kb/KbMultiCollectionRetrieveModal";
import {KbValidateCollectionModal} from "@/components/kb/KbValidateCollectionModal";
import {KbViewDocumentDrawer} from "@/components/kb/KbViewDocumentDrawer";
import {
    createKbCollection,
    deleteKbCollection,
    deleteKbDocument,
    getKbDocument,
    listAgentKbPolicySummaries,
    listKbDocuments,
    previewKbRetrieveMulti,
    renderKbDocument,
    validateKbCollectionDocuments,
    validateKbDocument,
} from "@/lib/kb/api";
import {loadKbBootstrapResources} from "@/lib/kb/bootstrap";
import {kbToolsByRuntimeName, normalizeKbRichHtmlForDetailView} from "@/lib/kb/kb-rich-html-display";
import type {
    KbAgentPolicySummaryDto,
    KbChunkStrategyMetaDto,
    KbCollectionDocumentsValidationResponse,
    KbCollectionDto,
    KbDocumentDto,
    KbDocumentValidationResponse,
    KbRetrievePreviewHitDto,
} from "@/lib/kb/types";
import {buildKbDocumentTableColumns} from "@/lib/kb/document-table-columns";
import type {ToolDto} from "@/lib/tools/types";
import {getTool, listToolsPage} from "@/lib/tools/api";
import {buildToolOutputsFromBlocks} from "@/lib/form-kv-helpers";
import {buildDocRenderInitialFromDocument, type DocRenderTestForm} from "@/lib/kb/doc-render-test";
import type {VectorStoreProfileDto} from "@/lib/vector-store/types";

import "@/components/kb/kb-quill-knowledge.css";
import kbShell from "@/components/kb/kb-shell.module.css";

/** 知识库 API 统一超时（与 `DEFAULT_REQUEST_TIMEOUT_MS` 一致）；需取消时与 `signal` 合并传入。 */
const KB_REQ = {timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS};

const KbIngestDocumentDrawer = dynamic(
    () =>
        import("@/components/kb/KbIngestDocumentDrawer").then((m) => ({
            default: m.KbIngestDocumentDrawer,
        })),
    {ssr: false, loading: () => null},
);

export default function KnowledgeBasePage() {
    const [error, setError] = React.useState<unknown>(null);
    const [vectorProfiles, setVectorProfiles] = React.useState<VectorStoreProfileDto[]>([]);
    const [collections, setCollections] = React.useState<KbCollectionDto[]>([]);
    const [documents, setDocuments] = React.useState<KbDocumentDto[]>([]);
    const [loadingCollections, setLoadingCollections] = React.useState(false);
    const [loadingDocs, setLoadingDocs] = React.useState(false);
    const [refreshing, setRefreshing] = React.useState(false);
    const [creating, setCreating] = React.useState(false);
    const [deletingCollectionId, setDeletingCollectionId] = React.useState<string | null>(null);
    const [deletingDocumentId, setDeletingDocumentId] = React.useState<string | null>(null);
    const [selectedCollectionId, setSelectedCollectionId] = React.useState<string | undefined>();
    const [collectionQuery, setCollectionQuery] = React.useState("");
    const [createModalOpen, setCreateModalOpen] = React.useState(false);
    const [ingestOpen, setIngestOpen] = React.useState(false);
    const [ingestEditDocumentId, setIngestEditDocumentId] = React.useState<string | undefined>();
    const [viewDocOpen, setViewDocOpen] = React.useState(false);
    const [viewDocLoading, setViewDocLoading] = React.useState(false);
    const [viewDocDetail, setViewDocDetail] = React.useState<KbDocumentDto | null>(null);
    const [docValidateLoading, setDocValidateLoading] = React.useState(false);
    const [docValidation, setDocValidation] = React.useState<KbDocumentValidationResponse | null>(null);
    const [docRetrieveQuery, setDocRetrieveQuery] = React.useState("");
    const [docRetrieveTopK, setDocRetrieveTopK] = React.useState(5);
    const [docRetrieveTh, setDocRetrieveTh] = React.useState(0.25);
    const [docRetrieveRender, setDocRetrieveRender] = React.useState(true);
    const [docRetrieveLoading, setDocRetrieveLoading] = React.useState(false);
    const [docRetrieveHits, setDocRetrieveHits] = React.useState<KbRetrievePreviewHitDto[] | null>(null);
    const [docRenderLoading, setDocRenderLoading] = React.useState(false);
    const [docRenderedMd, setDocRenderedMd] = React.useState<string | null>(null);
    const [retrieveModalOpen, setRetrieveModalOpen] = React.useState(false);
    const [collRetrieveQuery, setCollRetrieveQuery] = React.useState("");
    const [collRetrieveTopK, setCollRetrieveTopK] = React.useState(5);
    const [collRetrieveTh, setCollRetrieveTh] = React.useState(0.25);
    const [collRetrieveRender, setCollRetrieveRender] = React.useState(true);
    const [collRetrieveLoading, setCollRetrieveLoading] = React.useState(false);
    const [collRetrieveHits, setCollRetrieveHits] = React.useState<KbRetrievePreviewHitDto[] | null>(null);
    const [collRetrieveCollectionIds, setCollRetrieveCollectionIds] = React.useState<string[]>([]);
    const [agentKbSummaries, setAgentKbSummaries] = React.useState<KbAgentPolicySummaryDto[]>([]);
    const [agentKbSummariesLoading, setAgentKbSummariesLoading] = React.useState(false);
    const [agentPolicySelectNonce, setAgentPolicySelectNonce] = React.useState(0);
    const [validateCollectionModalOpen, setValidateCollectionModalOpen] = React.useState(false);
    const [collectionValidateLoading, setCollectionValidateLoading] = React.useState(false);
    const [collectionValidateResult, setCollectionValidateResult] =
        React.useState<KbCollectionDocumentsValidationResponse | null>(null);
    const [collectionValidateIncludeIssues, setCollectionValidateIncludeIssues] = React.useState(false);
    const [chunkMeta, setChunkMeta] = React.useState<KbChunkStrategyMetaDto[]>([]);

    const bootstrapAbortRef = React.useRef<AbortController | null>(null);
    React.useEffect(() => {
        return () => {
            bootstrapAbortRef.current?.abort();
        };
    }, []);

    /** 查看文档抽屉：解析 linkedToolIds 展示名称 */
    const [viewToolById, setViewToolById] = React.useState<Record<string, ToolDto>>({});
    /** 渲染测试 / 下拉：按需 getTool 合并完整 definition（含 outputSchema / parameters） */
    const [renderTestToolById, setRenderTestToolById] = React.useState<Record<string, ToolDto>>({});
    const [loadingViewTools, setLoadingViewTools] = React.useState(false);
    const [collectionForm] = Form.useForm<CreateCollectionForm>();
    const [docRenderForm] = Form.useForm<DocRenderTestForm>();
    const docRenderInitialValues = React.useMemo(
        () => buildDocRenderInitialFromDocument(viewDocDetail),
        [viewDocDetail],
    );

    /** 渲染测试：工具下拉选项（关联工具优先排序） */
    const docRenderMergedToolById = React.useMemo(
        () => ({...viewToolById, ...renderTestToolById}),
        [viewToolById, renderTestToolById],
    );

    const docRenderToolSelectOptions = React.useMemo(() => {
        const items = Object.values(viewToolById);
        const linked = viewDocDetail?.linkedToolIds ?? [];
        const linkedSet = new Set(linked);
        const sorted = [...items].sort((a, b) => {
            const la = linkedSet.has(a.id) ? 0 : 1;
            const lb = linkedSet.has(b.id) ? 0 : 1;
            if (la !== lb) {
                return la - lb;
            }
            const an = (a.displayLabel ?? a.name ?? "").trim();
            const bn = (b.displayLabel ?? b.name ?? "").trim();
            return an.localeCompare(bn, "zh-CN");
        });
        return sorted.map((t) => {
            const dn = (t.displayLabel ?? "").trim();
            const n = (t.name ?? "").trim();
            const label =
                dn && n && dn !== n ? `${dn} (${n})` : dn || n || `${t.id.slice(0, 8)}…`;
            return {value: t.id, label};
        });
    }, [viewToolById, viewDocDetail?.linkedToolIds]);
    const watchedVectorStoreProfileId = Form.useWatch("vectorStoreProfileId", collectionForm);
    const watchedProfileForCreate = React.useMemo(
        () => vectorProfiles.find((p) => p.id === watchedVectorStoreProfileId),
        [vectorProfiles, watchedVectorStoreProfileId],
    );

    const filteredCollections = React.useMemo(() => {
        const q = collectionQuery.trim().toLowerCase();
        if (!q) {
            return collections;
        }
        return collections.filter(
            (c) =>
                c.name.toLowerCase().includes(q) ||
                c.id.toLowerCase().includes(q) ||
                (c.embeddingModelId ?? "").toLowerCase().includes(q),
        );
    }, [collections, collectionQuery]);

    const selectedCollection = React.useMemo(
        () => collections.find((c) => c.id === selectedCollectionId),
        [collections, selectedCollectionId],
    );

    /** 多集合召回：仅展示与当前已选集合（第一个）相同 embedding 模型的集合，与后端策略一致 */
    const collRetrieveOptionsCollections = React.useMemo(() => {
        if (collRetrieveCollectionIds.length === 0) {
            return collections;
        }
        const anchor = collections.find((c) => c.id === collRetrieveCollectionIds[0]);
        const mid = anchor?.embeddingModelId;
        if (!mid) {
            return collections;
        }
        return collections.filter((c) => c.embeddingModelId === mid);
    }, [collections, collRetrieveCollectionIds]);

    React.useEffect(() => {
        if (!retrieveModalOpen) {
            return;
        }
        const ac = new AbortController();
        const fetchOpts = {signal: ac.signal, ...KB_REQ};
        setAgentKbSummariesLoading(true);
        void listAgentKbPolicySummaries(fetchOpts)
            .then((rows) => {
                if (!ac.signal.aborted) {
                    setAgentKbSummaries(rows);
                }
            })
            .catch(() => {
                if (!ac.signal.aborted) {
                    setAgentKbSummaries([]);
                }
            })
            .finally(() => {
                if (!ac.signal.aborted) {
                    setAgentKbSummariesLoading(false);
                }
            });
        return () => {
            ac.abort();
        };
    }, [retrieveModalOpen]);

    const reloadBootstrap = React.useCallback(async (opts?: { toast?: boolean }) => {
        bootstrapAbortRef.current?.abort();
        const ac = new AbortController();
        bootstrapAbortRef.current = ac;
        const fetchOpts = {signal: ac.signal, ...KB_REQ};
        setRefreshing(true);
        setLoadingCollections(true);
        setError(null);
        try {
            const {collections: cols, chunkStrategies: meta, vectorProfiles: profiles} =
                await loadKbBootstrapResources(fetchOpts);
            setCollections(cols);
            setChunkMeta(meta);
            setVectorProfiles(profiles);
            if (opts?.toast) {
                message.success("列表已刷新");
            }
        } catch (e) {
            if (!isAbortError(e)) {
                setError(e);
            }
        } finally {
            if (bootstrapAbortRef.current === ac) {
                bootstrapAbortRef.current = null;
                setRefreshing(false);
                setLoadingCollections(false);
            }
        }
    }, []);

    const applyChunkStrategyDefaults = React.useCallback(
        (strategy: string) => {
            const def = chunkMeta.find((x) => x.value === strategy);
            const p = def?.defaultParams ?? {};
            collectionForm.setFieldsValue({
                maxChars: typeof p.maxChars === "number" ? p.maxChars : 900,
                overlap: typeof p.overlap === "number" ? p.overlap : 0,
                headingLevel: typeof p.headingLevel === "number" ? p.headingLevel : 2,
                leadMaxChars: typeof p.leadMaxChars === "number" ? p.leadMaxChars : 512,
            });
        },
        [chunkMeta, collectionForm],
    );

    React.useEffect(() => {
        void reloadBootstrap();
    }, [reloadBootstrap]);

    React.useEffect(() => {
        if (!selectedCollectionId) {
            setDocuments([]);
            return;
        }
        const ac = new AbortController();
        let cancelled = false;
        setLoadingDocs(true);
        setError(null);
        void listKbDocuments(selectedCollectionId, {
            signal: ac.signal,
            ...KB_REQ,
        })
            .then((d) => {
                if (!cancelled) {
                    setDocuments(d);
                }
            })
            .catch((e) => {
                if (!cancelled && !ac.signal.aborted && !isAbortError(e)) {
                    setError(e);
                }
            })
            .finally(() => {
                if (!cancelled) {
                    setLoadingDocs(false);
                }
            });
        return () => {
            cancelled = true;
            ac.abort();
        };
    }, [selectedCollectionId]);

    /** 与 linkedToolIds 内容等价，避免在 effect 依赖里直接放数组引用 */
    const viewDocLinkedToolIdsKey = (viewDocDetail?.linkedToolIds ?? []).join("|");

    React.useEffect(() => {
        if (!viewDocOpen) {
            setRenderTestToolById({});
        }
    }, [viewDocOpen]);

    React.useEffect(() => {
        if (viewDocDetail?.id) {
            setRenderTestToolById({});
        }
    }, [viewDocDetail?.id]);

    /** 打开文档后预拉关联工具的完整定义，供渲染测试出参/入参下拉 */
    React.useEffect(() => {
        if (!viewDocOpen || !viewDocDetail?.id) {
            return;
        }
        const ids = viewDocDetail.linkedToolIds?.filter(Boolean) ?? [];
        if (ids.length === 0) {
            return;
        }
        const ac = new AbortController();
        const opts = {signal: ac.signal, ...KB_REQ};
        void Promise.all(
            ids.map((id) =>
                getTool(id, opts).then(
                    (t) => t,
                    () => null,
                ),
            ),
        ).then((list) => {
            if (ac.signal.aborted) {
                return;
            }
            setRenderTestToolById((prev) => {
                const next = {...prev};
                for (const t of list) {
                    if (t?.id) {
                        next[t.id] = t;
                    }
                }
                return next;
            });
        });
        return () => {
            ac.abort();
        };
        // viewDocLinkedToolIdsKey 已覆盖 linkedToolIds 内容变化
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [viewDocOpen, viewDocDetail?.id, viewDocLinkedToolIdsKey]);

    /** 打开文档详情时拉取工具列表，供正文解析名与「渲染测试」下工具/出参下拉 */
    React.useEffect(() => {
        if (!viewDocOpen) {
            setViewToolById({});
            setLoadingViewTools(false);
            return;
        }
        const ac = new AbortController();
        const opts = {signal: ac.signal, ...KB_REQ};
        setLoadingViewTools(true);
        void listToolsPage({page: 1, pageSize: 200}, opts)
            .then((p) => {
                if (ac.signal.aborted) {
                    return;
                }
                const next: Record<string, ToolDto> = {};
                for (const t of p.items ?? []) {
                    next[t.id] = t;
                }
                setViewToolById(next);
            })
            .catch(() => {
                if (!ac.signal.aborted) {
                    setViewToolById({});
                }
            })
            .finally(() => {
                if (!ac.signal.aborted) {
                    setLoadingViewTools(false);
                }
            });
        return () => {
            ac.abort();
        };
    }, [viewDocOpen, viewDocDetail?.id, viewDocLinkedToolIdsKey]);

    const [viewDetailRichHtmlSafe, setViewDetailRichHtmlSafe] = React.useState("");

    React.useEffect(() => {
        const raw = viewDocDetail?.bodyRich?.trim();
        if (!raw) {
            setViewDetailRichHtmlSafe("");
            return;
        }
        let cancelled = false;
        void import("dompurify").then(({default: DOMPurify}) => {
            if (cancelled) {
                return;
            }
            const toolsByName = kbToolsByRuntimeName(viewDocDetail?.linkedToolIds, viewToolById);
            const normalized = normalizeKbRichHtmlForDetailView(raw, {toolsByName});
            const safe = DOMPurify.sanitize(normalized, {
                USE_PROFILES: {html: true},
                ADD_ATTR: [
                    "data-type",
                    "data-tool-code",
                    "data-tool-field",
                    "data-kb-display",
                    "data-kb-field-desc",
                    "data-kb-tool",
                    "data-kb-placeholder",
                ],
            });
            if (!cancelled) {
                setViewDetailRichHtmlSafe(safe);
            }
        });
        return () => {
            cancelled = true;
        };
    }, [viewDocDetail?.bodyRich, viewDocDetail?.linkedToolIds, viewToolById]);

    async function onCreateCollection(values: CreateCollectionForm) {
        setCreating(true);
        setError(null);
        try {
            const chunkParams: Record<string, unknown> = {
                maxChars: values.maxChars,
                overlap: values.overlap ?? 0,
            };
            if (values.chunkStrategy === "HEADING_SECTION") {
                chunkParams.headingLevel = values.headingLevel ?? 2;
                chunkParams.leadMaxChars = values.leadMaxChars ?? 512;
                chunkParams.overlap = 0;
            }
            const override = (values.collectionNameOverride ?? "").trim();
            const created = await createKbCollection(
                {
                    name: values.name,
                    description: values.description ?? "",
                    vectorStoreProfileId: values.vectorStoreProfileId,
                    ...(override ? {vectorStoreConfig: {collectionName: override}} : {}),
                    chunkStrategy: values.chunkStrategy,
                    chunkParams,
                },
                KB_REQ,
            );
            message.success("集合已创建");
            collectionForm.resetFields();
            setCollections((prev) => [created, ...prev.filter((c) => c.id !== created.id)]);
            setCreateModalOpen(false);
            setSelectedCollectionId(created.id);
        } catch (e) {
            setError(e);
        } finally {
            setCreating(false);
        }
    }

    async function openViewDocument(docId: string) {
        if (!selectedCollectionId) {
            return;
        }
        setViewDocOpen(true);
        setViewDocLoading(true);
        setViewDocDetail(null);
        setViewToolById({});
        setDocValidation(null);
        setDocRetrieveHits(null);
        setDocRenderedMd(null);
        setError(null);
        try {
            const d = await getKbDocument(selectedCollectionId, docId, KB_REQ);
            setViewDocDetail(d);
        } catch (e) {
            setError(e);
            setViewDocOpen(false);
        } finally {
            setViewDocLoading(false);
        }
    }

    async function onValidateCurrentDocument() {
        if (!selectedCollectionId || !viewDocDetail?.id) {
            return;
        }
        setDocValidateLoading(true);
        setError(null);
        try {
            const r = await validateKbDocument(selectedCollectionId, viewDocDetail.id, KB_REQ);
            setDocValidation(r);
            const err = (r.issues ?? []).filter((i) => i.severity === "ERROR").length;
            if (err > 0) {
                message.warning(`校验完成：发现 ${err} 个错误`);
            } else {
                message.success("校验完成");
            }
        } catch (e) {
            setError(e);
        } finally {
            setDocValidateLoading(false);
        }
    }

    async function onDocRetrievePreview() {
        const cid = viewDocDetail?.collectionId ?? selectedCollectionId;
        if (!cid || !docRetrieveQuery.trim()) {
            message.warning("请输入查询文本");
            return;
        }
        setDocRetrieveLoading(true);
        setError(null);
        try {
            const r = await previewKbRetrieveMulti(
                {
                    collectionIds: [cid],
                    query: docRetrieveQuery.trim(),
                    topK: docRetrieveTopK,
                    scoreThreshold: docRetrieveTh,
                    renderSnippets: docRetrieveRender,
                },
                KB_REQ,
            );
            setDocRetrieveHits(r.hits ?? []);
            message.success(`召回 ${(r.hits ?? []).length} 条`);
        } catch (e) {
            setError(e);
        } finally {
            setDocRetrieveLoading(false);
        }
    }

    async function onCollRetrievePreview() {
        if (collRetrieveCollectionIds.length === 0) {
            message.warning("请至少选择一个知识集合");
            return;
        }
        if (!collRetrieveQuery.trim()) {
            message.warning("请输入查询文本");
            return;
        }
        setCollRetrieveLoading(true);
        setError(null);
        try {
            const r = await previewKbRetrieveMulti(
                {
                    collectionIds: collRetrieveCollectionIds,
                    query: collRetrieveQuery.trim(),
                    topK: collRetrieveTopK,
                    scoreThreshold: collRetrieveTh,
                    renderSnippets: collRetrieveRender,
                },
                KB_REQ,
            );
            setCollRetrieveHits(r.hits ?? []);
            message.success(`召回 ${(r.hits ?? []).length} 条（${collRetrieveCollectionIds.length} 个集合）`);
        } catch (e) {
            setError(e);
        } finally {
            setCollRetrieveLoading(false);
        }
    }

    async function onValidateEntireCollection() {
        if (!selectedCollectionId) {
            return;
        }
        setCollectionValidateLoading(true);
        setError(null);
        try {
            const r = await validateKbCollectionDocuments(
                selectedCollectionId,
                {
                    includeIssues: collectionValidateIncludeIssues,
                },
                KB_REQ,
            );
            setCollectionValidateResult(r);
            const bad = r.documentsWithErrors ?? 0;
            if (bad > 0) {
                message.warning(`校验完成：${bad} 篇含错误`);
            } else {
                message.success(`已校验 ${r.totalDocuments} 篇文档`);
            }
        } catch (e) {
            setError(e);
        } finally {
            setCollectionValidateLoading(false);
        }
    }

    async function onDocRenderPreview() {
        if (!selectedCollectionId || !viewDocDetail?.id) {
            return;
        }
        let toolOutputs: Record<string, unknown>;
        try {
            const v = await docRenderForm.validateFields();
            toolOutputs = buildToolOutputsFromBlocks(v.toolBlocks ?? []);
            if (Object.keys(toolOutputs).length === 0) {
                message.warning("请至少选择一个工具并填写出参字段 mock 值");
                return;
            }
        } catch {
            return;
        }
        setDocRenderLoading(true);
        setError(null);
        try {
            const r = await renderKbDocument(selectedCollectionId, viewDocDetail.id, {toolOutputs}, KB_REQ);
            setDocRenderedMd(r.renderedBody ?? "");
            message.success("渲染完成");
        } catch (e) {
            setError(e);
        } finally {
            setDocRenderLoading(false);
        }
    }

    async function onDeleteCollection(collectionId: string) {
        setDeletingCollectionId(collectionId);
        setError(null);
        try {
            const del = await deleteKbCollection(collectionId, KB_REQ);
            const n = typeof del?.agentsPolicyUpdated === "number" ? del.agentsPolicyUpdated : 0;
            message.success(
                n > 0
                    ? `已删除集合；已更新 ${n} 个智能体的知识库策略`
                    : "已删除集合（级联文档与分片）",
            );
            if (selectedCollectionId === collectionId) {
                setSelectedCollectionId(undefined);
            }
            setCollections((prev) => prev.filter((c) => c.id !== collectionId));
        } catch (e) {
            setError(e);
        } finally {
            setDeletingCollectionId(null);
        }
    }

    async function onDeleteDocument(documentId: string) {
        if (!selectedCollectionId) {
            return;
        }
        setDeletingDocumentId(documentId);
        setError(null);
        try {
            await deleteKbDocument(selectedCollectionId, documentId, KB_REQ);
            message.success("已删除文档");
            setDocuments((prev) => prev.filter((d) => d.id !== documentId));
        } catch (e) {
            setError(e);
        } finally {
            setDeletingDocumentId(null);
        }
    }

    const docColumns = buildKbDocumentTableColumns({
        selectedCollectionId,
        deletingDocumentId,
        onViewDocument: openViewDocument,
        onEditIngest: (documentId) => {
            setIngestEditDocumentId(documentId);
            setIngestOpen(true);
        },
        onDeleteDocument,
    });

    return (
        <AppLayout>
            <PageShell gap={20}>
                <div className={kbShell.pageIntro}>
                    <PageHeaderBlock
                        icon={<BookOutlined/>}
                        title="知识库"
                        subtitle="按「集合」隔离语料与向量空间；绑定文本嵌入模型后写入文档即可分片入库。在智能体中通过 knowledge_base_policy 引用集合以启用检索增强（RAG）。"
                        extra={
                            <Button
                                icon={<ReloadOutlined/>}
                                loading={refreshing}
                                onClick={() => void reloadBootstrap({toast: true})}
                            >
                                刷新
                            </Button>
                        }
                    />
                </div>

                <ErrorAlert error={error}/>

                <Row gutter={[20, 20]}>
                    <Col xs={24} lg={6}>
                        <KbCollectionListPanel
                            chunkMeta={chunkMeta}
                            collectionQuery={collectionQuery}
                            onCollectionQueryChange={setCollectionQuery}
                            filteredCollections={filteredCollections}
                            loadingCollections={loadingCollections}
                            selectedCollectionId={selectedCollectionId}
                            onSelectCollection={setSelectedCollectionId}
                            deletingCollectionId={deletingCollectionId}
                            onDeleteCollection={onDeleteCollection}
                            onOpenCreateModal={() => setCreateModalOpen(true)}
                        />
                    </Col>

                    <Col xs={24} lg={18}>
                        <KbDocumentsMainPanel
                            chunkMeta={chunkMeta}
                            collections={collections}
                            selectedCollection={selectedCollection}
                            selectedCollectionId={selectedCollectionId}
                            documents={documents}
                            loadingDocs={loadingDocs}
                            docColumns={docColumns}
                            onOpenRetrieveModal={() => {
                                setCollRetrieveHits(null);
                                setCollRetrieveCollectionIds(selectedCollectionId ? [selectedCollectionId] : []);
                                setRetrieveModalOpen(true);
                            }}
                            onOpenValidateCollectionModal={() => {
                                setCollectionValidateResult(null);
                                setValidateCollectionModalOpen(true);
                            }}
                            onOpenIngest={() => {
                                setIngestEditDocumentId(undefined);
                                setIngestOpen(true);
                            }}
                        />
                    </Col>
                </Row>

                <KbCreateCollectionModal
                    open={createModalOpen}
                    onCancel={() => setCreateModalOpen(false)}
                    onFinish={onCreateCollection}
                    form={collectionForm}
                    creating={creating}
                    vectorProfiles={vectorProfiles}
                    chunkMeta={chunkMeta}
                    watchedProfileForCreate={watchedProfileForCreate}
                    applyChunkStrategyDefaults={applyChunkStrategyDefaults}
                />

                <KbIngestDocumentDrawer
                    open={ingestOpen}
                    onClose={() => {
                        setIngestOpen(false);
                        setIngestEditDocumentId(undefined);
                    }}
                    collectionId={selectedCollectionId}
                    collectionName={selectedCollection?.name}
                    editDocumentId={ingestEditDocumentId}
                    onSuccess={(doc) => {
                        setDocuments((prev) => {
                            const i = prev.findIndex((d) => d.id === doc.id);
                            if (i >= 0) {
                                const next = [...prev];
                                next[i] = doc;
                                return next;
                            }
                            return [doc, ...prev];
                        });
                    }}
                    onError={setError}
                />

                <KbViewDocumentDrawer
                    open={viewDocOpen}
                    onClose={() => {
                        setViewDocOpen(false);
                        setViewDocDetail(null);
                        setViewToolById({});
                        setDocValidation(null);
                        setDocRetrieveHits(null);
                        setDocRenderedMd(null);
                    }}
                    viewDocLoading={viewDocLoading}
                    viewDocDetail={viewDocDetail}
                    selectedCollectionId={selectedCollectionId}
                    viewToolById={viewToolById}
                    loadingViewTools={loadingViewTools}
                    viewDetailRichHtmlSafe={viewDetailRichHtmlSafe}
                    onEditFromDrawer={() => {
                        const id = viewDocDetail!.id;
                        setViewDocOpen(false);
                        setViewDocDetail(null);
                        setViewToolById({});
                        setIngestEditDocumentId(id);
                        setIngestOpen(true);
                    }}
                    docValidateLoading={docValidateLoading}
                    docValidation={docValidation}
                    onValidateCurrentDocument={onValidateCurrentDocument}
                    docRetrieveQuery={docRetrieveQuery}
                    setDocRetrieveQuery={setDocRetrieveQuery}
                    docRetrieveTopK={docRetrieveTopK}
                    setDocRetrieveTopK={setDocRetrieveTopK}
                    docRetrieveTh={docRetrieveTh}
                    setDocRetrieveTh={setDocRetrieveTh}
                    docRetrieveRender={docRetrieveRender}
                    setDocRetrieveRender={setDocRetrieveRender}
                    docRetrieveLoading={docRetrieveLoading}
                    docRetrieveHits={docRetrieveHits}
                    onDocRetrievePreview={onDocRetrievePreview}
                    docRenderForm={docRenderForm}
                    docRenderInitialValues={docRenderInitialValues}
                    docRenderToolSelectOptions={docRenderToolSelectOptions}
                    docRenderMergedToolById={docRenderMergedToolById}
                    onRenderTestToolLoaded={(t) =>
                        setRenderTestToolById((prev) => ({...prev, [t.id]: t}))
                    }
                    docRenderLoading={docRenderLoading}
                    docRenderedMd={docRenderedMd}
                    onDocRenderPreview={onDocRenderPreview}
                />


                <KbMultiCollectionRetrieveModal
                    open={retrieveModalOpen}
                    onClose={() => setRetrieveModalOpen(false)}
                    onModalOpenChange={(open) => {
                        if (!open) {
                            setCollRetrieveHits(null);
                        } else {
                            setAgentPolicySelectNonce((n) => n + 1);
                        }
                    }}
                    agentPolicySelectNonce={agentPolicySelectNonce}
                    collections={collections}
                    collRetrieveOptionsCollections={collRetrieveOptionsCollections}
                    collRetrieveCollectionIds={collRetrieveCollectionIds}
                    onCollRetrieveCollectionIdsChange={setCollRetrieveCollectionIds}
                    collRetrieveQuery={collRetrieveQuery}
                    onCollRetrieveQueryChange={setCollRetrieveQuery}
                    collRetrieveTopK={collRetrieveTopK}
                    onCollRetrieveTopKChange={setCollRetrieveTopK}
                    collRetrieveTh={collRetrieveTh}
                    onCollRetrieveThChange={setCollRetrieveTh}
                    collRetrieveRender={collRetrieveRender}
                    onCollRetrieveRenderChange={setCollRetrieveRender}
                    collRetrieveLoading={collRetrieveLoading}
                    collRetrieveHits={collRetrieveHits}
                    onCollRetrievePreview={onCollRetrievePreview}
                    agentKbSummaries={agentKbSummaries}
                    agentKbSummariesLoading={agentKbSummariesLoading}
                />


                <KbValidateCollectionModal
                    open={validateCollectionModalOpen}
                    onClose={() => setValidateCollectionModalOpen(false)}
                    title={
                        selectedCollection
                            ? `整集合校验 · ${selectedCollection.name}`
                            : "整集合校验"
                    }
                    selectedCollectionId={selectedCollectionId}
                    collectionValidateIncludeIssues={collectionValidateIncludeIssues}
                    onCollectionValidateIncludeIssuesChange={setCollectionValidateIncludeIssues}
                    collectionValidateLoading={collectionValidateLoading}
                    onValidateEntireCollection={onValidateEntireCollection}
                    collectionValidateResult={collectionValidateResult}
                    onAfterOpenChange={(open) => {
                        if (!open) {
                            setCollectionValidateResult(null);
                        }
                    }}
                />
            </PageShell>
        </AppLayout>
    );
}
