"use client";

import {CloudDownloadOutlined} from "@ant-design/icons";
import {Alert, Button, Checkbox, Input, Modal, Space, Table, Typography, message} from "antd";
import type {ColumnsType} from "antd/es/table";
import React from "react";

import {ApiError} from "@/lib/api/types";
import {batchImportMcpTools, fetchRemoteMcpTools} from "@/lib/tools/api";
import type {RemoteMcpToolMetaDto} from "@/lib/tools/types";

type Props = {
    open: boolean;
    onClose: () => void;
    /** 导入成功后回调（刷新列表等） */
    onSuccess: () => void | Promise<void>;
    /** 打开时预填 SSE 地址（例如表单里的 mcpEndpoint） */
    defaultEndpoint?: string;
};

export function McpBatchImportModal(props: Props) {
    const {open, onClose, onSuccess, defaultEndpoint} = props;
    const [endpoint, setEndpoint] = React.useState("");
    const [refresh, setRefresh] = React.useState(false);
    const [loadingList, setLoadingList] = React.useState(false);
    const [importing, setImporting] = React.useState(false);
    const [rows, setRows] = React.useState<RemoteMcpToolMetaDto[]>([]);
    const [selected, setSelected] = React.useState<React.Key[]>([]);
    const [namePrefix, setNamePrefix] = React.useState("");
    const [skipExisting, setSkipExisting] = React.useState(true);

    React.useEffect(() => {
        if (open) {
            setEndpoint((defaultEndpoint ?? "").trim());
            setRefresh(false);
            setRows([]);
            setSelected([]);
            setNamePrefix("");
            setSkipExisting(true);
        }
    }, [open, defaultEndpoint]);

    async function handleDiscover() {
        const ep = endpoint.trim();
        if (!ep) {
            message.warning("请填写远端 MCP SSE 根地址");
            return;
        }
        setLoadingList(true);
        try {
            const list = await fetchRemoteMcpTools(ep, refresh);
            setRows(list);
            setSelected(list.map((r) => r.name));
            message.success(list.length ? `已发现 ${list.length} 个工具` : "远端未返回工具");
        } catch (e) {
            message.error("拉取失败，请检查地址与网络");
            throw e;
        } finally {
            setLoadingList(false);
        }
    }

    async function runImport(body: Parameters<typeof batchImportMcpTools>[0]) {
        setImporting(true);
        try {
            const resp = await batchImportMcpTools(body);
            const c = resp.created?.length ?? 0;
            const s = resp.skipped?.length ?? 0;
            message.success(`新建 ${c} 个，跳过 ${s} 个`);
            if (s > 0 && resp.skipped?.length) {
                Modal.info({
                    title: "跳过项",
                    width: 560,
                    content: (
                        <ul style={{maxHeight: 240, overflow: "auto", paddingLeft: 20}}>
                            {resp.skipped.map((x) => (
                                <li key={`${x.name}-${x.reason}`}>
                                    <Typography.Text code>{x.name}</Typography.Text> — {x.reason}
                                </li>
                            ))}
                        </ul>
                    ),
                });
            }
            await onSuccess();
            onClose();
        } catch (e) {
            const msg = e instanceof ApiError ? e.message : "批量导入失败";
            message.error(msg);
        } finally {
            setImporting(false);
        }
    }

    async function handleImportSelected() {
        const ep = endpoint.trim();
        if (!ep) {
            message.warning("请填写 SSE 地址");
            return;
        }
        if (selected.length === 0) {
            message.warning("请至少勾选一个远端工具，或先点击「拉取列表」");
            return;
        }
        await runImport({
            endpoint: ep,
            remoteToolNames: selected as string[],
            namePrefix: namePrefix.trim() || undefined,
            skipExisting,
        });
    }

    /** 不传 remoteToolNames 时后端按远端 tools/list 全部导入 */
    async function handleImportAllRemote() {
        const ep = endpoint.trim();
        if (!ep) {
            message.warning("请填写 SSE 地址");
            return;
        }
        await runImport({
            endpoint: ep,
            namePrefix: namePrefix.trim() || undefined,
            skipExisting,
        });
    }

    const columns: ColumnsType<RemoteMcpToolMetaDto> = [
        {title: "远端工具名", dataIndex: "name", width: 200, ellipsis: true},
        {title: "说明", dataIndex: "description", ellipsis: true},
    ];

    return (
        <Modal
            title={
                <Space>
                    <CloudDownloadOutlined/>
                    <span>从外部 MCP 批量导入工具</span>
                </Space>
            }
            open={open}
            onCancel={onClose}
            width={800}
            footer={
                <Space wrap>
                    <Button onClick={onClose}>取消</Button>
                    <Button loading={importing} onClick={() => void handleImportAllRemote()}>
                        导入远端全部
                    </Button>
                    <Button
                        type="primary"
                        loading={importing}
                        disabled={selected.length === 0}
                        onClick={() => void handleImportSelected()}
                    >
                        导入选中（{selected.length}）
                    </Button>
                </Space>
            }
            destroyOnHidden
        >
            <Space orientation="vertical" size={12} style={{width: "100%"}}>
                <Alert
                    type="info"
                    showIcon
                    message="说明"
                    description={
                        <>
                            填写外部 MCP 的 SSE 根 URL，先「拉取列表」再勾选导入。平台会为每条工具创建 MCP 类型记录（definition.endpoint +
                            mcpToolName，并尽量写入 description / inputSchema）。生产环境若需禁止访问内网地址，请将后端{" "}
                            <Typography.Text code>agentlego.mcp.client.strict-ssrf</Typography.Text> 设为 true。
                        </>
                    }
                />
                <Space wrap style={{width: "100%"}} align="start">
                    <Input
                        style={{minWidth: 280, flex: 1}}
                        placeholder="例如 http://127.0.0.1:8080/mcp"
                        value={endpoint}
                        onChange={(e) => setEndpoint(e.target.value)}
                    />
                    <Checkbox checked={refresh} onChange={(e) => setRefresh(e.target.checked)}>
                        忽略缓存重新拉取
                    </Checkbox>
                    <Button type="primary" loading={loadingList} onClick={() => void handleDiscover()}>
                        拉取列表
                    </Button>
                </Space>
                <Space wrap align="center">
                    <Typography.Text type="secondary">平台名前缀（可选）</Typography.Text>
                    <Input
                        style={{width: 160}}
                        placeholder="如 ext_"
                        value={namePrefix}
                        onChange={(e) => setNamePrefix(e.target.value)}
                    />
                    <Checkbox checked={skipExisting} onChange={(e) => setSkipExisting(e.target.checked)}>
                        已存在同名则跳过
                    </Checkbox>
                </Space>
                <Table<RemoteMcpToolMetaDto>
                    size="small"
                    rowKey="name"
                    loading={loadingList}
                    dataSource={rows}
                    columns={columns}
                    pagination={{pageSize: 8}}
                    rowSelection={{
                        selectedRowKeys: selected,
                        onChange: (keys) => setSelected(keys),
                    }}
                />
            </Space>
        </Modal>
    );
}
