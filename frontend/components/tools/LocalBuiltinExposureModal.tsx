"use client";

import {Button, Empty, Modal, Space, Spin, Switch, Table, Tooltip, Typography, message} from "antd";
import type {ColumnsType} from "antd/es/table";
import React from "react";

import {DEFAULT_REQUEST_TIMEOUT_MS} from "@/lib/api/request";
import {isAbortError} from "@/lib/api/isAbortError";
import {fetchLocalBuiltinExposureSettings, updateLocalBuiltinExposure} from "@/lib/tools/api";
import type {LocalBuiltinExposureRowDto} from "@/lib/tools/types";

type Props = {
    open: boolean;
    onClose: () => void;
    /** 保存成功后（含开关影响 /tools/meta/local-builtins） */
    onSaved?: () => void | Promise<void>;
};

export function LocalBuiltinExposureModal(props: Props) {
    const {open, onClose, onSaved} = props;
    const [loading, setLoading] = React.useState(false);
    const [saving, setSaving] = React.useState(false);
    const [rows, setRows] = React.useState<LocalBuiltinExposureRowDto[]>([]);

    React.useEffect(() => {
        if (!open) {
            return;
        }
        const ac = new AbortController();
        setLoading(true);
        void (async () => {
            try {
                const data = await fetchLocalBuiltinExposureSettings({
                    signal: ac.signal,
                    timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS,
                });
                if (!ac.signal.aborted) {
                    setRows(data);
                }
            } catch (e) {
                if (!isAbortError(e) && !ac.signal.aborted) {
                    console.error(e);
                    message.error("加载内置暴露配置失败");
                }
            } finally {
                if (!ac.signal.aborted) {
                    setLoading(false);
                }
            }
        })();
        return () => ac.abort();
    }, [open]);

    function patchRow(name: string, patch: Partial<Pick<LocalBuiltinExposureRowDto, "exposeMcp" | "showInUi">>) {
        setRows((prev) =>
            prev.map((r) => (r.name === name ? {...r, ...patch} : r)),
        );
    }

    async function handleSave() {
        setSaving(true);
        try {
            await updateLocalBuiltinExposure(
                {
                    items: rows.map((r) => ({
                        toolName: r.name,
                        exposeMcp: r.exposeMcp,
                        showInUi: r.showInUi,
                    })),
                },
                {timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS},
            );
            message.success("已保存");
            await onSaved?.();
            onClose();
        } catch (e) {
            console.error(e);
            message.error("保存失败");
        } finally {
            setSaving(false);
        }
    }

    const columns: ColumnsType<LocalBuiltinExposureRowDto> = [
        {
            title: "内置名",
            dataIndex: "name",
            width: 160,
            ellipsis: true,
            render: (v: string) => (
                <Typography.Text code copyable={{text: v}} ellipsis>
                    {v}
                </Typography.Text>
            ),
        },
        {
            title: "标签",
            dataIndex: "label",
            width: 120,
            ellipsis: true,
            render: (v: string | undefined) => v?.trim() || "—",
        },
        {
            title: "说明",
            dataIndex: "description",
            ellipsis: true,
            render: (v: string | undefined) => {
                const t = v?.trim();
                if (!t) {
                    return <Typography.Text type="secondary">—</Typography.Text>;
                }
                return (
                    <Tooltip title={t}>
                        <Typography.Text ellipsis style={{maxWidth: 280}}>
                            {t}
                        </Typography.Text>
                    </Tooltip>
                );
            },
        },
        {
            title: (
                <Tooltip title="本机 MCP Server 的 tools/list 是否列出该内置">
                    <span>MCP 暴露</span>
                </Tooltip>
            ),
            key: "exposeMcp",
            width: 120,
            align: "center",
            render: (_: unknown, record) => (
                <Switch
                    checked={record.exposeMcp}
                    onChange={(checked) => patchRow(record.name, {exposeMcp: checked})}
                />
            ),
        },
        {
            title: (
                <Tooltip title="管理端创建 LOCAL 工具时的下拉是否展示该内置">
                    <span>管理端展示</span>
                </Tooltip>
            ),
            key: "showInUi",
            width: 120,
            align: "center",
            render: (_: unknown, record) => (
                <Switch
                    checked={record.showInUi}
                    onChange={(checked) => patchRow(record.name, {showInUi: checked})}
                />
            ),
        },
    ];

    return (
        <Modal
            title="内置工具暴露"
            open={open}
            onCancel={onClose}
            width={880}
            destroyOnHidden
            footer={
                <Space>
                    <Button onClick={onClose}>取消</Button>
                    <Button
                        type="primary"
                        loading={saving}
                        onClick={() => void handleSave()}
                        disabled={loading || rows.length === 0}
                    >
                        保存
                    </Button>
                </Space>
            }
        >
            <Typography.Paragraph type="secondary" style={{marginBottom: 12}}>
                控制已注册进程内 <Typography.Text code>@Tool</Typography.Text> 是否出现在本机 MCP 列表，以及是否在「新建
                LOCAL 工具」下拉中展示。与后端 <Typography.Text code>/tools/meta/local-builtins/exposure</Typography.Text>{" "}
                对齐。
            </Typography.Paragraph>
            <Spin spinning={loading}>
                {rows.length === 0 && !loading ? (
                    <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description="当前无已注册的内置工具；在代码中向 LocalBuiltinTools 添加 @Tool 后将出现在此列表。"
                    />
                ) : (
                    <Table<LocalBuiltinExposureRowDto>
                        size="small"
                        rowKey="name"
                        columns={columns}
                        dataSource={rows}
                        pagination={false}
                        scroll={{x: 720}}
                    />
                )}
            </Spin>
        </Modal>
    );
}
