"use client";

import "@uiw/react-md-editor/markdown-editor.css";

import dynamic from "next/dynamic";
import React from "react";

const MDEditor = dynamic(async () => (await import("@uiw/react-md-editor")).default, {
    ssr: false,
    loading: () => (
        <div
            style={{
                height: 420,
                background: "#f5f5f5",
                borderRadius: 8,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                color: "rgba(0,0,0,0.45)",
            }}
        >
            编辑器加载中…
        </div>
    ),
});

export type KbMarkdownFieldProps = {
    value?: string;
    onChange?: (v?: string) => void;
    /** 编辑区高度（px） */
    height?: number;
};

/**
 * 受控 Markdown 编辑器（@uiw/react-md-editor），用于知识库文档正文。
 */
export function KbMarkdownField({value, onChange, height = 440}: KbMarkdownFieldProps) {
    return (
        <div data-color-mode="light" style={{borderRadius: 8, overflow: "hidden"}}>
            <MDEditor value={value ?? ""} onChange={onChange} height={height} visibleDragbar={false}/>
        </div>
    );
}
