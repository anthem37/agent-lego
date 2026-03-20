"use client";

import {Button, Popconfirm, Typography} from "antd";
import React from "react";

import {fetchToolReferences} from "@/lib/tools/api";
import type {ToolReferencesDto} from "@/lib/tools/types";

type Props = {
    toolId: string;
    /** 删除中（外层状态） */
    deleting?: boolean;
    onConfirm: () => void | Promise<void>;
    trigger: React.ReactElement;
};

/**
 * 打开时拉取 /tools/{id}/references，有智能体引用则禁止确认删除。
 */
export function DeleteToolPopconfirm(props: Props) {
    const {toolId, deleting, onConfirm, trigger} = props;
    const [refs, setRefs] = React.useState<ToolReferencesDto | null>(null);
    const [loadingRefs, setLoadingRefs] = React.useState(false);

    async function loadRefs() {
        setLoadingRefs(true);
        setRefs(null);
        try {
            const r = await fetchToolReferences(toolId);
            setRefs(r);
        } catch {
            setRefs(null);
        } finally {
            setLoadingRefs(false);
        }
    }

    const blocked = refs != null && refs.referencingAgentCount > 0;
    const desc = loadingRefs ? (
        <Typography.Text type="secondary">正在检查智能体引用…</Typography.Text>
    ) : refs == null ? (
        <Typography.Text type="secondary">打开时将检查是否仍被智能体 toolIds 引用。</Typography.Text>
    ) : blocked ? (
        <span>
            <Typography.Text type="danger">
                无法删除：仍有 {refs.referencingAgentCount} 个智能体在 toolIds 中引用此工具。
            </Typography.Text>
            {refs.referencingAgentIds.length > 0 ? (
                <div style={{marginTop: 8}}>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        示例智能体 ID（最多 20 条）：{" "}
                        <Typography.Text code>{refs.referencingAgentIds.slice(0, 5).join(", ")}</Typography.Text>
                        {refs.referencingAgentIds.length > 5 ? " …" : ""}
                    </Typography.Text>
                </div>
            ) : null}
        </span>
    ) : (
        <Typography.Text>未被智能体引用。删除后不可恢复。</Typography.Text>
    );

    return (
        <Popconfirm
            title="确定删除该工具？"
            description={desc}
            onConfirm={() => void onConfirm()}
            okText="删除"
            cancelText="取消"
            okButtonProps={{
                danger: true,
                loading: deleting,
                disabled: loadingRefs || blocked,
            }}
            onOpenChange={(open) => {
                if (open) {
                    void loadRefs();
                }
            }}
        >
            {trigger}
        </Popconfirm>
    );
}

/** 列表行常用：文字链样式触发器 */
export function DeleteToolLink(props: Omit<Props, "trigger"> & { children?: React.ReactNode }) {
    const {children = "删除", ...rest} = props;
    return (
        <DeleteToolPopconfirm
            {...rest}
            trigger={
                <Button type="link" size="small" danger style={{padding: 0}}>
                    {children}
                </Button>
            }
        />
    );
}
