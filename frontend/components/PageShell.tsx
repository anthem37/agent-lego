"use client";

import {Space} from "antd";
import React from "react";

/**
 * 全站统一页面纵向栈：间距与宽度一致，避免各页手写 Space。
 */
export function PageShell(props: {
    children: React.ReactNode;
    /** 区块间距，默认 20 */
    gap?: number;
    className?: string;
    style?: React.CSSProperties;
}) {
    const {gap = 20, className, style} = props;
    return (
        <Space orientation="vertical" size={gap} className={className} style={{width: "100%", ...style}}>
            {props.children}
        </Space>
    );
}
