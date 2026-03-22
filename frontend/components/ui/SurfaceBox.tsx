"use client";

import React from "react";

/**
 * 正文预览、代码块等：统一描边与圆角，避免各页手写 rgba(0,0,0,0.06)。
 */
export function SurfaceBox(
    props: React.PropsWithChildren<{
        className?: string;
        /** 默认 12，与知识库富文本预览一致 */
        padding?: number;
        style?: React.CSSProperties;
        /** 默认 var(--app-radius-sm) */
        radius?: number | string;
        dangerouslySetInnerHTML?: { __html: string };
    }>,
) {
    const pad = props.padding ?? 12;
    const r = props.radius ?? "var(--app-radius-sm)";
    return (
        <div
            className={props.className}
            style={{
                padding: pad,
                border: "1px solid var(--app-border)",
                borderRadius: r,
                background: "var(--app-surface)",
                ...props.style,
            }}
            dangerouslySetInnerHTML={props.dangerouslySetInnerHTML}
        >
            {props.dangerouslySetInnerHTML ? null : props.children}
        </div>
    );
}
