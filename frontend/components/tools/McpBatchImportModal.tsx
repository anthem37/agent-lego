"use client";

import {CloudDownloadOutlined} from "@ant-design/icons";
import {Alert, Button, Checkbox, Input, message, Modal, Space, Table, Typography} from "antd";
import type {ColumnsType} from "antd/es/table";
import React from "react";

import {DEFAULT_REQUEST_TIMEOUT_MS} from "@/lib/api/request";
import {ApiError} from "@/lib/api/types";
import {batchImportMcpTools, fetchRemoteMcpTools} from "@/lib/tools/api";
import {PLATFORM_TOOL_NAME_PATTERN, sanitizePlatformToolName} from "@/lib/tools/mcp-platform-name";
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
    const [platformNameByRemote, setPlatformNameByRemote] = React.useState<Record<string, string>>({});
    /** true = 与库中重名时记入 skipped；false = 记入 nameConflicts，便于改表内平台名后重试 */
    const [skipExisting, setSkipExisting] = React.useState(false);

    React.useEffect(() => {
        if (open) {
            setEndpoint((defaultEndpoint ?? "").trim());
            setRefresh(false);
            setRows([]);
            setSelected([]);
            setNamePrefix("");
            setPlatformNameByRemote({});
            setSkipExisting(false);
        }
    }, [open, defaultEndpoint]);

    function applyPrefixToPlatformColumn() {
        const pref = namePrefix.trim();
        setPlatformNameByRemote((prev) => {
            const next = {...prev};
            for (const r of rows) {
                next[r.name] = sanitizePlatformToolName(pref, r.name);
            }
            return next;
        });
    }

    function buildPlatformMapForRemotes(remoteNames: string[]): Record<string, string> {
        const pref = namePrefix.trim();
        const out: Record<string, string> = {};
        for (const k of remoteNames) {
            const raw = platformNameByRemote[k] ?? sanitizePlatformToolName(pref, k);
            out[k] = raw.trim();
        }
        return out;
    }

    async function handleDiscover() {
        const ep = endpoint.trim();
        if (!ep) {
            message.warning("请填写远端 MCP SSE 根地址");
            return;
        }
        setLoadingList(true);
        try {
            const list = await fetchRemoteMcpTools(ep, refresh, {timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS});
            setRows(list);
            setSelected(list.map((r) => r.name));
            const pref = namePrefix.trim();
            const m: Record<string, string> = {};
            for (const r of list) {
                m[r.name] = sanitizePlatformToolName(pref, r.name);
            }
            setPlatformNameByRemote(m);
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
            const resp = await batchImportMcpTools(body, {timeoutMs: DEFAULT_REQUEST_TIMEOUT_MS});
            const c = resp.created?.length ?? 0;
            const s = resp.skipped?.length ?? 0;
            const nc = resp.nameConflicts?.length ?? 0;

            if (c > 0) {
                await onSuccess();
            }

            if (nc > 0) {
                message.warning(`有 ${nc} 条未导入（名称冲突或格式无效），请修改表格中的「平台工具名」后再次导入`);
                setSelected(resp.nameConflicts!.map((x) => x.remoteToolName));
                Modal.warning({
                    title: "未导入条目（可改名后重试）",
                    width: 640,
                    content: (
                        <ul style={{maxHeight: 280, overflow: "auto", paddingLeft: 20}}>
                            {resp.nameConflicts!.map((x) => (
                                <li key={`${x.remoteToolName}-${x.attemptedPlatformName}`}>
                                    远端 <Typography.Text code>{x.remoteToolName}</Typography.Text> → 平台名{" "}
                                    <Typography.Text code>{x.attemptedPlatformName}</Typography.Text>：{x.reason}
                                </li>
                            ))}
                        </ul>
                    ),
                });
            } else if (c > 0 || s > 0) {
                message.success(`新建 ${c} 个${s > 0 ? `，跳过 ${s} 个` : ""}`);
            }

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

            if (nc === 0) {
                onClose();
            }
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
        const keys = selected.map(String);
        for (const k of keys) {
            const nm = (platformNameByRemote[k] ?? "").trim();
            if (!PLATFORM_TOOL_NAME_PATTERN.test(nm)) {
                message.error(`远端「${k}」的平台工具名无效：须字母开头，仅含字母、数字、下划线、短横线`);
                return;
            }
        }
        await runImport({
            endpoint: ep,
            remoteToolNames: keys,
            namePrefix: namePrefix.trim() || undefined,
            platformNamesByRemote: buildPlatformMapForRemotes(keys),
            skipExisting,
        });
    }

    async function handleImportAllRemote() {
        const ep = endpoint.trim();
        if (!ep) {
            message.warning("请填写 SSE 地址");
            return;
        }
        if (rows.length === 0) {
            message.warning("请先拉取列表");
            return;
        }
        const keys = rows.map((r) => r.name);
        for (const k of keys) {
            const nm = (platformNameByRemote[k] ?? "").trim();
            if (!PLATFORM_TOOL_NAME_PATTERN.test(nm)) {
                message.error(`远端「${k}」的平台工具名无效：须字母开头，仅含字母、数字、下划线、短横线`);
                return;
            }
        }
        await runImport({
            endpoint: ep,
            namePrefix: namePrefix.trim() || undefined,
            platformNamesByRemote: buildPlatformMapForRemotes(keys),
            skipExisting,
        });
    }

    const columns: ColumnsType<RemoteMcpToolMetaDto> = [
        {title: "远端工具名", dataIndex: "name", width: 160, ellipsis: true},
        {
            title: "平台工具名（可改）",
            key: "platformName",
            width: 220,
            render: (_, r) => (
                <Input
                    size="small"
                    placeholder="字母开头…"
                    value={platformNameByRemote[r.name] ?? ""}
                    onChange={(e) =>
                        setPlatformNameByRemote((prev) => ({
                            ...prev,
                            [r.name]: e.target.value,
                        }))
                    }
                />
            ),
        },
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
            width={920}
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
                    title="说明"
                    description={
                        <>
                            拉取列表后可在「平台工具名」列直接改名以避免与已有工具全平台重名；默认不会因重名单条抛错，冲突项会返回{" "}
                            <Typography.Text code>nameConflicts</Typography.Text>，改完再点导入即可。
                            勾选「同名则跳过」适合无人值守批量导入。生产环境请将后端{" "}
                            <Typography.Text code>agentlego.mcp.client.strict-ssrf</Typography.Text> 设为 true 以禁止内网地址。
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
                        style={{width: 140}}
                        placeholder="如 ext_"
                        value={namePrefix}
                        onChange={(e) => setNamePrefix(e.target.value)}
                    />
                    <Button size="small" onClick={applyPrefixToPlatformColumn} disabled={rows.length === 0}>
                        用当前前缀重算平台名
                    </Button>
                    <Checkbox checked={skipExisting} onChange={(e) => setSkipExisting(e.target.checked)}>
                        同名则跳过（记入 skipped，不打断其它条）
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
