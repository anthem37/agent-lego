"use client";

import dynamic from "next/dynamic";

import "@uiw/react-md-editor/markdown-editor.css";

import {KB_KNOWLEDGE_EDITOR_TOTAL_PX} from "@/components/kb/knowledge-editor-layout";

const MDEditor = dynamic(async () => (await import("@uiw/react-md-editor")).default, {
    ssr: false,
    loading: () => (
        <div
            style={{
                minHeight: KB_KNOWLEDGE_EDITOR_TOTAL_PX,
                background: "var(--ant-color-fill-quaternary, #f5f5f5)",
                borderRadius: 8,
            }}
        />
    ),
});

export type KbMarkdownEditorProps = {
    value?: string;
    onChange?: (markdown: string) => void;
};

/** Markdown 编辑（分屏预览），仅客户端渲染 */
export function KbMarkdownEditor({value, onChange}: KbMarkdownEditorProps) {
    return (
        <div
            className="kb-md-editor"
            data-color-mode="light"
            style={{borderRadius: "var(--ant-border-radius-lg, 8px)", overflow: "hidden"}}
        >
            <MDEditor
                value={value ?? ""}
                onChange={(v) => onChange?.(typeof v === "string" ? v : "")}
                height={KB_KNOWLEDGE_EDITOR_TOTAL_PX}
                visibleDragbar={false}
            />
        </div>
    );
}
