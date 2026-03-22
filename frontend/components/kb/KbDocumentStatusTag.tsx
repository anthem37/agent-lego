"use client";

import {Tag} from "antd";
import React from "react";

/** 文档状态：READY / PENDING / PROCESSING / FAILED */
export function KbDocumentStatusTag(props: { status: string }) {
    const s = (props.status ?? "").toUpperCase();
    if (s === "READY") {
        return <Tag color="success">就绪</Tag>;
    }
    if (s === "PENDING" || s === "PROCESSING") {
        return <Tag color="processing">处理中</Tag>;
    }
    if (s === "FAILED") {
        return <Tag color="error">失败</Tag>;
    }
    return <Tag>{props.status || "—"}</Tag>;
}
