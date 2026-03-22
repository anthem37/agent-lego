"use client";

import "quill/dist/quill.snow.css";
import "./kb-quill-knowledge.css";

import Quill from "quill";
import React from "react";

import {
    KB_KNOWLEDGE_TAG_BLOT,
    kbKnowledgeTagClipboardMatchers,
    type KbTagValue,
    registerKbKnowledgeQuillFormats,
} from "@/components/kb/kb-quill-formats";
import {applyKbQuillSnowChineseUi} from "@/components/kb/kb-quill-zh-ui";
import {KB_QUILL_TOOLBAR_FIELD_SVG, KB_QUILL_TOOLBAR_TOOL_SVG} from "@/components/kb/kb-quill-toolbar-icons";

export type KbRichTextFieldHandle = {
    /** 类链接：插入工具标签 data-type=tool */
    insertToolTag: (toolCode: string, displayText?: string) => void;
    /** 插入工具字段（点分路径，如 data.orderNo）；后端转为 $.data.orderNo 并生成占位绑定 */
    insertToolFieldTag: (
        toolCode: string,
        toolFieldPath: string,
        displayText?: string,
        fieldDescription?: string,
    ) => void;
    focus: () => void;
};

/** 工具栏末尾「插入工具 / 插入字段」回调（由业务层打开选工具或级联选字段） */
export type KbToolbarExtraHandlers = {
    onInsertTool?: () => void;
    onInsertField?: () => void;
    insertToolDisabled?: boolean;
    insertFieldDisabled?: boolean;
};

export type KbRichTextFieldProps = {
    value?: string;
    onChange?: (html: string) => void;
    /** 编辑区最小高度（px） */
    minHeight?: number;
    toolbarExtras?: KbToolbarExtraHandlers;
};

/**
 * 知识库富文本：工具/工具字段以类链接内嵌存储。
 * 挂载/卸载时清空包装 DOM，避免 React Strict Mode 或抽屉重开时重复插入 Quill 工具栏（双工具栏）。
 */
export const KbRichTextField = React.forwardRef<KbRichTextFieldHandle, KbRichTextFieldProps>(
    function KbRichTextField({value, onChange, minHeight = 360, toolbarExtras}, ref) {
        const wrapRef = React.useRef<HTMLDivElement | null>(null);
        const quillRef = React.useRef<Quill | null>(null);
        const onChangeRef = React.useRef(onChange);
        const toolbarExtrasRef = React.useRef(toolbarExtras);
        const syncingFromProps = React.useRef(false);
        const minHeightRef = React.useRef(minHeight);

        React.useEffect(() => {
            onChangeRef.current = onChange;
        }, [onChange]);

        React.useEffect(() => {
            minHeightRef.current = minHeight;
        }, [minHeight]);

        React.useEffect(() => {
            toolbarExtrasRef.current = toolbarExtras;
        }, [toolbarExtras]);

        const emitHtml = React.useCallback(() => {
            const q = quillRef.current;
            if (!q) {
                return;
            }
            onChangeRef.current?.(q.root.innerHTML);
        }, []);

        const insertTag = React.useCallback(
            (payload: KbTagValue) => {
                const q = quillRef.current;
                if (!q) {
                    return;
                }
                q.focus();
                const range = q.getSelection(true);
                const index = range ? range.index : q.getLength();
                q.insertEmbed(index, KB_KNOWLEDGE_TAG_BLOT, payload, Quill.sources.USER);
                q.setSelection(index + 1, 0, Quill.sources.SILENT);
                emitHtml();
            },
            [emitHtml],
        );

        React.useImperativeHandle(
            ref,
            () => ({
                insertToolTag: (toolCode: string, displayText?: string) => {
                    const c = String(toolCode ?? "").trim();
                    if (!c) {
                        return;
                    }
                    const d = displayText?.trim();
                    insertTag({kind: "tool", code: c, ...(d ? {displayText: d} : {})});
                },
                insertToolFieldTag: (
                    toolCode: string,
                    toolFieldPath: string,
                    displayText?: string,
                    fieldDescription?: string,
                ) => {
                    const c = String(toolCode ?? "").trim();
                    const f = String(toolFieldPath ?? "").trim();
                    if (!c || !f) {
                        return;
                    }
                    const d = displayText?.trim();
                    const fd = fieldDescription?.trim();
                    insertTag({
                        kind: "tool_field",
                        code: c,
                        field: f,
                        ...(d ? {displayText: d} : {}),
                        ...(fd ? {fieldDescription: fd} : {}),
                    });
                },
                focus: () => {
                    quillRef.current?.focus();
                },
            }),
            [insertTag],
        );

        React.useEffect(() => {
            const wrap = wrapRef.current;
            if (!wrap) {
                return;
            }
            wrap.innerHTML = "";
            const host = document.createElement("div");
            host.style.minHeight = `${minHeightRef.current}px`;
            host.style.background = "#fff";
            wrap.appendChild(host);

            registerKbKnowledgeQuillFormats();
            const q = new Quill(host, {
                theme: "snow",
                modules: {
                    toolbar: [
                        [{header: [1, 2, 3, false]}],
                        ["bold", "italic", "underline", "strike"],
                        [{list: "ordered"}, {list: "bullet"}],
                        [{indent: "-1"}, {indent: "+1"}],
                        ["blockquote", "code-block"],
                        ["link"],
                        ["clean"],
                    ],
                },
            });
            quillRef.current = q;
            requestAnimationFrame(() => {
                applyKbQuillSnowChineseUi(wrap);
            });
            if (toolbarExtrasRef.current) {
                const tb = q.getModule("toolbar") as { container?: HTMLElement } | undefined;
                if (tb?.container) {
                    const span = document.createElement("span");
                    span.className = "ql-formats kb-quill-toolbar-extras";
                    const mkBtn = (svgHtml: string, title: string, kind: "tool" | "field") => {
                        const b = document.createElement("button");
                        b.type = "button";
                        b.className = kind === "tool" ? "ql-kb-insert-tool" : "ql-kb-insert-field";
                        b.title = title;
                        b.setAttribute("aria-label", title);
                        b.innerHTML = svgHtml;
                        b.addEventListener("click", (e) => {
                            e.preventDefault();
                            const x = toolbarExtrasRef.current;
                            if (!x) {
                                return;
                            }
                            if (kind === "tool") {
                                if (x.insertToolDisabled) {
                                    return;
                                }
                                x.onInsertTool?.();
                            } else {
                                if (x.insertFieldDisabled) {
                                    return;
                                }
                                x.onInsertField?.();
                            }
                        });
                        return b;
                    };
                    span.appendChild(
                        mkBtn(KB_QUILL_TOOLBAR_TOOL_SVG, "插入工具引用（需先在右侧绑定工具）", "tool"),
                    );
                    span.appendChild(
                        mkBtn(KB_QUILL_TOOLBAR_FIELD_SVG, "插入工具出参字段（级联选择）", "field"),
                    );
                    tb.container.appendChild(span);
                }
            }
            const kbd = q.getModule("keyboard") as { bindings: Record<string, unknown[]> };
            const delEmbed = (index: number) => {
                q.deleteText(index, 1, Quill.sources.USER);
                emitHtml();
            };
            const kbTagBackspaceBinding = {
                key: "Backspace",
                altKey: null,
                ctrlKey: null,
                metaKey: null,
                shiftKey: null,
                collapsed: true,
                handler(range: { index: number; length: number }) {
                    if (!range || range.length > 0 || range.index < 1) {
                        return true;
                    }
                    const fmt = q.getFormat(range.index - 1, 1);
                    if (fmt[KB_KNOWLEDGE_TAG_BLOT]) {
                        delEmbed(range.index - 1);
                        q.setSelection(range.index - 1, 0, Quill.sources.SILENT);
                        return false;
                    }
                    return true;
                },
            };
            const kbTagDeleteBinding = {
                key: "Delete",
                altKey: null,
                ctrlKey: null,
                metaKey: null,
                shiftKey: null,
                collapsed: true,
                handler(range: { index: number; length: number }) {
                    if (!range || range.length > 0) {
                        return true;
                    }
                    if (range.index >= q.getLength() - 1) {
                        return true;
                    }
                    const fmt = q.getFormat(range.index, 1);
                    if (fmt[KB_KNOWLEDGE_TAG_BLOT]) {
                        delEmbed(range.index);
                        q.setSelection(range.index, 0, Quill.sources.SILENT);
                        return false;
                    }
                    return true;
                },
            };
            kbd.bindings.Backspace?.unshift(kbTagBackspaceBinding as unknown);
            kbd.bindings.Delete?.unshift(kbTagDeleteBinding as unknown);
            for (const [sel, fn] of kbKnowledgeTagClipboardMatchers()) {
                q.clipboard.addMatcher(sel, fn);
            }
            q.on("text-change", () => {
                if (syncingFromProps.current) {
                    return;
                }
                emitHtml();
            });

            return () => {
                quillRef.current = null;
                wrap.innerHTML = "";
            };
        }, [emitHtml]);

        React.useEffect(() => {
            const wrap = wrapRef.current;
            if (!wrap) {
                return;
            }
            const toolBtn = wrap.querySelector(".ql-kb-insert-tool") as HTMLButtonElement | null;
            const fieldBtn = wrap.querySelector(".ql-kb-insert-field") as HTMLButtonElement | null;
            if (toolBtn) {
                toolBtn.disabled = !!toolbarExtras?.insertToolDisabled;
            }
            if (fieldBtn) {
                fieldBtn.disabled = !!toolbarExtras?.insertFieldDisabled;
            }
        }, [toolbarExtras?.insertToolDisabled, toolbarExtras?.insertFieldDisabled]);

        React.useEffect(() => {
            const q = quillRef.current;
            if (!q) {
                return;
            }
            const root = q.root as HTMLElement;
            if (root?.parentElement) {
                const container = root.parentElement;
                if (container.classList.contains("ql-container")) {
                    container.style.minHeight = `${minHeight}px`;
                }
            }
        }, [minHeight]);

        React.useEffect(() => {
            const q = quillRef.current;
            if (!q) {
                return;
            }
            const next = value ?? "";
            const cur = q.root.innerHTML;
            if (next === cur) {
                return;
            }
            syncingFromProps.current = true;
            const sel = q.getSelection();
            q.setContents([], Quill.sources.SILENT);
            if (next.trim()) {
                const delta = q.clipboard.convert({html: next});
                q.setContents(delta, Quill.sources.SILENT);
            }
            if (sel) {
                try {
                    q.setSelection(sel);
                } catch {
                    /* ignore */
                }
            }
            syncingFromProps.current = false;
        }, [value]);

        return (
            <div
                ref={wrapRef}
                className="kb-quill-wrap"
                style={{borderRadius: 8, overflow: "hidden", border: "1px solid #e8e8e8"}}
            />
        );
    },
);

KbRichTextField.displayName = "KbRichTextField";
