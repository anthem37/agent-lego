"use client";

import "@uiw/react-md-editor/markdown-editor.css";

import dynamic from "next/dynamic";
import React from "react";

const MarkdownBody = dynamic(
    async () => {
        const {default: MDEditor} = await import("@uiw/react-md-editor");

        function Inner({source}: { source: string }) {
            return <MDEditor.Markdown source={source}/>;
        }

        return Inner;
    },
    {
        ssr: false,
        loading: () => <span style={{color: "rgba(0,0,0,0.45)"}}>预览加载中…</span>,
    },
);

export type KbMarkdownPreviewProps = {
    source: string;
    className?: string;
    style?: React.CSSProperties;
};

/** 只读 Markdown 渲染（与编辑器同主题）。 */
export function KbMarkdownPreview({source, className, style}: KbMarkdownPreviewProps) {
    return (
        <div
            data-color-mode="light"
            className={className}
            style={{
                padding: "12px 16px",
                background: "#fafafa",
                borderRadius: 8,
                border: "1px solid #f0f0f0",
                maxHeight: "min(70vh, 640px)",
                overflow: "auto",
                ...style,
            }}
        >
            <MarkdownBody source={source}/>
        </div>
    );
}
