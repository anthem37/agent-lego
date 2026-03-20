"use client";

import {MinusCircleOutlined, PlusOutlined} from "@ant-design/icons";
import {
    Alert,
    Button,
    Checkbox,
    Descriptions,
    Drawer,
    Form,
    Input,
    message,
    Modal,
    Select,
    Space,
    Switch,
    Typography,
} from "antd";
import React from "react";

import {DRAWER_WIDTH_COMPLEX} from "@/lib/ui/sizes";
import {ErrorAlert} from "@/components/ErrorAlert";
import {LocalBuiltinIoPreview} from "@/components/tools/LocalBuiltinIoPreview";
import {McpBatchImportModal} from "@/components/tools/McpBatchImportModal";
import {createTool, updateTool} from "@/lib/tools/api";
import {
    buildToolDefinition,
    defaultHttpParameterRow,
    extractHttpUrlPlaceholderNames,
    HTTP_METHOD_OPTIONS,
    HTTP_PARAM_TYPE_OPTIONS,
    NAME_ID_RULES,
    toolDtoToFormValues,
    validateHttpOutputFieldRows,
    validateHttpParameterRows,
    validateHttpUrlPlaceholdersHaveParameterRows,
} from "@/lib/tools/form";
import {toolTypeDisplayName} from "@/lib/tool-labels";
import type {LocalBuiltinToolMetaDto, ToolDto, ToolFormValues, ToolTypeMetaDto} from "@/lib/tools/types";

type Props = {
    open: boolean;
    mode: "create" | "edit";
    /** 编辑模式下必填 */
    editingTool: ToolDto | null;
    toolTypeMeta: ToolTypeMetaDto[];
    /** 后端扫描得到的 LOCAL 内置工具（下拉与提示） */
    localBuiltins: LocalBuiltinToolMetaDto[];
    onClose: () => void;
    /** 保存成功后回调（列表刷新等） */
    onSaved: () => void | Promise<void>;
};

function defaultLocalBuiltinName(builtins: LocalBuiltinToolMetaDto[]): string {
    const n = builtins[0]?.name?.trim();
    return n && n.length > 0 ? n : "echo";
}

export function ToolFormDrawer(props: Props) {
    const {open, mode, editingTool, toolTypeMeta, localBuiltins, onClose, onSaved} = props;
    const [form] = Form.useForm<ToolFormValues>();
    const [submitting, setSubmitting] = React.useState(false);
    const [localError, setLocalError] = React.useState<unknown>(null);
    const [mcpBulkOpen, setMcpBulkOpen] = React.useState(false);
    const [mcpBulkDefaultEndpoint, setMcpBulkDefaultEndpoint] = React.useState<string | undefined>(undefined);

    const watchedToolType = Form.useWatch("toolType", form) ?? "LOCAL";
    const watchedHttpMethod = Form.useWatch("httpMethod", form) ?? "GET";
    const watchedPreserveHttpParams = Form.useWatch("httpParametersAdvancedPreserve", form) === true;
    const watchedPreserveHttpOutput = Form.useWatch("httpOutputSchemaAdvancedPreserve", form) === true;
    const watchedPreserveMcpParams = Form.useWatch("mcpParametersAdvancedPreserve", form) === true;

    const typeOptions = React.useMemo(() => {
        if (toolTypeMeta.length > 0) {
            return toolTypeMeta.map((m) => ({
                value: m.code,
                label: `${m.label}（${m.code}）`,
            }));
        }
        return [
            {value: "LOCAL", label: "本地内置（LOCAL）"},
            {value: "HTTP", label: "HTTP 请求（HTTP）"},
            {value: "MCP", label: "MCP（MCP）"},
            {value: "WORKFLOW", label: "工作流（WORKFLOW）"},
        ];
    }, [toolTypeMeta]);

    const metaByCode = React.useMemo(() => {
        const m = new Map<string, ToolTypeMetaDto>();
        toolTypeMeta.forEach((x) => m.set(x.code.toUpperCase(), x));
        return m;
    }, [toolTypeMeta]);

    React.useEffect(() => {
        if (!open) {
            return;
        }
        setLocalError(null);
        if (mode === "edit" && editingTool) {
            const partial = toolDtoToFormValues(editingTool);
            form.setFieldsValue({
                toolType: partial.toolType ?? "LOCAL",
                name: partial.name ?? "",
                toolDescription: partial.toolDescription,
                mcpEndpoint: partial.mcpEndpoint,
                mcpRemoteToolName: partial.mcpRemoteToolName,
                httpUrl: partial.httpUrl,
                httpMethod: partial.httpMethod ?? "GET",
                httpHeaderRows: partial.httpHeaderRows?.length ? partial.httpHeaderRows : [{headerName: "", value: ""}],
                httpParameterRows: partial.httpParameterRows?.length
                    ? partial.httpParameterRows
                    : [defaultHttpParameterRow()],
                sendJsonBody: partial.sendJsonBody !== false,
                workflowId: partial.workflowId,
                httpParametersAdvancedPreserve:
                    partial.toolType === "HTTP" ? partial.httpParametersAdvancedPreserve === true : false,
                httpOutputParameterRows: partial.httpOutputParameterRows?.length
                    ? partial.httpOutputParameterRows
                    : [defaultHttpParameterRow()],
                httpOutputSchemaAdvancedPreserve:
                    partial.toolType === "HTTP" ? partial.httpOutputSchemaAdvancedPreserve === true : false,
                mcpParameterRows: partial.mcpParameterRows?.length
                    ? partial.mcpParameterRows
                    : [defaultHttpParameterRow()],
                mcpParametersAdvancedPreserve:
                    partial.toolType === "MCP" ? partial.mcpParametersAdvancedPreserve === true : false,
            } as ToolFormValues);
        } else if (mode === "create") {
            form.resetFields();
            form.setFieldsValue({
                toolType: "LOCAL",
                name: defaultLocalBuiltinName(localBuiltins),
                httpMethod: "GET",
                sendJsonBody: true,
                httpHeaderRows: [{headerName: "", value: ""}],
                httpParameterRows: [defaultHttpParameterRow()],
                httpParametersAdvancedPreserve: false,
                httpOutputParameterRows: [defaultHttpParameterRow()],
                httpOutputSchemaAdvancedPreserve: false,
                mcpParameterRows: [defaultHttpParameterRow()],
                mcpParametersAdvancedPreserve: false,
            } as Partial<ToolFormValues>);
        }
    }, [open, mode, editingTool, form]);

    /** LOCAL 内置列表异步到达时，修正无效的默认 name，避免整表被重复 reset */
    React.useEffect(() => {
        if (!open || mode !== "create") {
            return;
        }
        if (form.getFieldValue("toolType") !== "LOCAL") {
            return;
        }
        if (localBuiltins.length === 0) {
            return;
        }
        const cur = form.getFieldValue("name");
        if (!localBuiltins.some((b) => b.name === cur)) {
            form.setFieldsValue({name: defaultLocalBuiltinName(localBuiltins)});
        }
    }, [open, mode, localBuiltins, form]);

    /** POST/PUT/PATCH 时「发送 JSON 请求体」Switch 才挂载；避免 sendJsonBody 长期 undefined 被当成 false */
    React.useEffect(() => {
        if (!open || watchedToolType !== "HTTP") {
            return;
        }
        const m = String(watchedHttpMethod).toUpperCase();
        if (!["POST", "PUT", "PATCH"].includes(m)) {
            return;
        }
        if (form.getFieldValue("sendJsonBody") === undefined) {
            form.setFieldsValue({sendJsonBody: true});
        }
    }, [open, watchedToolType, watchedHttpMethod, form]);

    function resetForToolType(next: ToolFormValues["toolType"]) {
        const clear: Partial<ToolFormValues> = {
            mcpEndpoint: undefined,
            mcpRemoteToolName: undefined,
            mcpParameterRows: [defaultHttpParameterRow()],
            mcpParametersAdvancedPreserve: false,
            httpUrl: undefined,
            workflowId: undefined,
            toolDescription: undefined,
            httpHeaderRows: [{headerName: "", value: ""}],
            httpParameterRows: [defaultHttpParameterRow()],
            httpParametersAdvancedPreserve: false,
            httpOutputParameterRows: [defaultHttpParameterRow()],
            httpOutputSchemaAdvancedPreserve: false,
        };
        if (next === "LOCAL") {
            form.setFieldsValue({
                ...clear,
                name: defaultLocalBuiltinName(localBuiltins),
                httpMethod: "GET",
                sendJsonBody: true,
            });
        } else if (next === "HTTP") {
            form.setFieldsValue({
                ...clear,
                name: "",
                httpMethod: "GET",
                sendJsonBody: true,
            });
        } else if (next === "MCP") {
            form.setFieldsValue({
                ...clear,
                name: "",
                mcpEndpoint: "",
                mcpRemoteToolName: "",
                mcpParameterRows: [defaultHttpParameterRow()],
                mcpParametersAdvancedPreserve: false,
                httpMethod: "GET",
                sendJsonBody: true,
            });
        } else if (next === "WORKFLOW") {
            form.setFieldsValue({
                ...clear,
                name: "",
                workflowId: "",
                httpMethod: "GET",
                sendJsonBody: true,
            });
        }
    }

    async function onFinish(values: ToolFormValues) {
        if (values.toolType === "HTTP" && values.httpParametersAdvancedPreserve !== true) {
            const paramErr = validateHttpParameterRows(values.httpParameterRows);
            if (paramErr) {
                message.error(paramErr);
                return;
            }
            const urlPhErr = validateHttpUrlPlaceholdersHaveParameterRows(
                values.httpUrl ?? "",
                values.httpParameterRows,
            );
            if (urlPhErr) {
                message.error(urlPhErr);
                return;
            }
            if (values.httpOutputSchemaAdvancedPreserve !== true) {
                const outErr = validateHttpOutputFieldRows(values.httpOutputParameterRows);
                if (outErr) {
                    message.error(outErr);
                    return;
                }
            }
        }
        if (values.toolType === "MCP" && values.mcpParametersAdvancedPreserve !== true) {
            const mcpParamErr = validateHttpParameterRows(values.mcpParameterRows);
            if (mcpParamErr) {
                message.error(mcpParamErr);
                return;
            }
        }
        setLocalError(null);
        setSubmitting(true);
        try {
            const definition = buildToolDefinition(values, {
                existingDefinition:
                    mode === "edit" && editingTool?.definition
                        ? (editingTool.definition as Record<string, unknown>)
                        : undefined,
            });
            const body = {
                toolType: values.toolType,
                name: values.name.trim(),
                definition,
            };
            if (mode === "create") {
                await createTool(body);
            } else if (editingTool) {
                await updateTool(editingTool.id, body);
            }
            await onSaved();
            onClose();
        } catch (e) {
            setLocalError(e);
        } finally {
            setSubmitting(false);
        }
    }

    const currentMeta = metaByCode.get(String(watchedToolType).toUpperCase());
    const showHttpBodySwitch =
        watchedToolType === "HTTP" &&
        ["POST", "PUT", "PATCH"].includes(String(watchedHttpMethod).toUpperCase());

    return (
        <Drawer
            title={
                mode === "create" ? (
                    "新建工具"
                ) : (
                    <Space direction="vertical" size={0}>
                        <span>编辑工具</span>
                        {editingTool ? (
                            <Typography.Text type="secondary" style={{fontSize: 12}} copyable>
                                {editingTool.id}
                            </Typography.Text>
                        ) : null}
                    </Space>
                )
            }
            size={DRAWER_WIDTH_COMPLEX}
            open={open}
            onClose={onClose}
            destroyOnHidden
            extra={
                <Space>
                    <Button onClick={onClose}>取消</Button>
                    <Button type="primary" loading={submitting} onClick={() => void form.submit()}>
                        {mode === "create" ? "创建" : "保存修改"}
                    </Button>
                </Space>
            }
        >
            <ErrorAlert error={localError}/>

            {mode === "edit" && editingTool ? (
                <Descriptions size="small" bordered column={1} style={{marginBottom: 16}}>
                    <Descriptions.Item label="当前类型">
                        <Typography.Text>
                            {toolTypeDisplayName(editingTool.toolType)}{" "}
                            <Typography.Text type="secondary">({editingTool.toolType})</Typography.Text>
                        </Typography.Text>
                    </Descriptions.Item>
                    <Descriptions.Item label="test-call">
                        {currentMeta?.supportsTestCall === false ? (
                            <Typography.Text type="warning">当前类型预期不支持联调</Typography.Text>
                        ) : (
                            <Typography.Text type="success">支持在详情页联调</Typography.Text>
                        )}
                    </Descriptions.Item>
                </Descriptions>
            ) : null}

            <Typography.Paragraph type="secondary" style={{marginTop: 0}}>
                同一 <Typography.Text code>toolType + name</Typography.Text>{" "}
                组合全局唯一。修改类型或名称后请同步检查已引用该工具 ID 的智能体配置。
            </Typography.Paragraph>

            <Form<ToolFormValues>
                key={mode === "edit" ? editingTool?.id ?? "e" : "create"}
                form={form}
                layout="vertical"
                onFinish={(v) => void onFinish(v)}
                onValuesChange={(changed) => {
                    if (changed.toolType != null) {
                        resetForToolType(changed.toolType as ToolFormValues["toolType"]);
                    }
                    if (changed.httpMethod != null) {
                        const m = String(changed.httpMethod).toUpperCase();
                        if (["POST", "PUT", "PATCH"].includes(m) && form.getFieldValue("sendJsonBody") === undefined) {
                            form.setFieldsValue({sendJsonBody: true});
                        }
                    }
                }}
            >
                <Form.Item name="toolType" label="工具类型" rules={[{required: true, message: "请选择类型"}]}>
                    <Select
                        options={typeOptions}
                        placeholder="选择类型"
                        disabled={false}
                        showSearch
                        optionFilterProp="label"
                    />
                </Form.Item>

                {watchedToolType === "LOCAL" ? (
                    <>
                        <Alert
                            type={localBuiltins.length === 0 ? "warning" : "info"}
                            showIcon
                            title="本地内置"
                            description={
                                localBuiltins.length === 0
                                    ? "未能从接口加载内置工具列表，请刷新或检查后端 /tools/meta/local-builtins。"
                                    : `当前进程内置：${localBuiltins.map((b) => b.name).join("、")}；名称须与列表一致。`
                            }
                            style={{marginBottom: 16}}
                        />
                        <Form.Item name="name" label="内置工具" rules={[{required: true, message: "请选择"}]}>
                            <Select
                                disabled={localBuiltins.length === 0}
                                options={localBuiltins.map((b) => ({
                                    value: b.name,
                                    label: (b.label && b.label.trim()) || b.name,
                                }))}
                            />
                        </Form.Item>
                        <Form.Item shouldUpdate noStyle>
                            {() => {
                                const n = form.getFieldValue("name");
                                const opt = localBuiltins.find((b) => b.name === n);
                                if (!opt) {
                                    return null;
                                }
                                const hint = opt.usageHint?.trim();
                                return (
                                    <Space direction="vertical" size={8} style={{width: "100%", marginTop: -8}}>
                                        {hint ? (
                                            <Typography.Paragraph type="secondary" style={{marginBottom: 0}}>
                                                {hint}
                                            </Typography.Paragraph>
                                        ) : null}
                                        <LocalBuiltinIoPreview meta={opt} showTitle/>
                                    </Space>
                                );
                            }}
                        </Form.Item>
                    </>
                ) : null}

                {watchedToolType === "HTTP" ? (
                    <>
                        <Form.Item name="httpParametersAdvancedPreserve" valuePropName="checked" hidden>
                            <Checkbox/>
                        </Form.Item>
                        <Form.Item name="httpOutputSchemaAdvancedPreserve" valuePropName="checked" hidden>
                            <Checkbox/>
                        </Form.Item>
                        <Form.Item name="name" label="工具名称（name）" rules={NAME_ID_RULES}>
                            <Input placeholder="如 fetch_weather"/>
                        </Form.Item>
                        <Form.Item name="httpUrl" label="请求 URL" rules={[{required: true, message: "必填"}]}>
                            <Input placeholder="https://... 花括号占位符如 {city} 会由模型填入"/>
                        </Form.Item>
                        <Form.Item name="httpMethod" label="HTTP 方法" rules={[{required: true}]}>
                            <Select options={HTTP_METHOD_OPTIONS.map((m) => ({value: m, label: m}))}/>
                        </Form.Item>
                        {watchedPreserveHttpParams ? (
                            <>
                                <Alert
                                    type="warning"
                                    showIcon
                                    title="高级入参已保留（未用表格编辑）"
                                    description={
                                        <>
                                            当前工具在{" "}
                                            <Typography.Text code>definition.parameters</Typography.Text> 或{" "}
                                            <Typography.Text code>inputSchema</Typography.Text>{" "}
                                            中存在<strong>无法用本页表格无损表达</strong>的内容（例如{" "}
                                            <Typography.Text code>$ref</Typography.Text>、
                                            <Typography.Text code>allOf</Typography.Text>、嵌套对象类型等）。
                                            你仍可在下方修改 URL、请求头等；<strong>保存时不会覆盖</strong>
                                            上述高级入参字段。请在「工具详情 → 概览」查看完整 definition，或通过 API 维护。
                                        </>
                                    }
                                    style={{marginBottom: 12}}
                                />
                                <Space direction="vertical" size={8} style={{width: "100%", marginBottom: 16}}>
                                    <Button
                                        type="primary"
                                        ghost
                                        onClick={() => {
                                            Modal.confirm({
                                                title: "改为表单配置入参？",
                                                content:
                                                    "确认后，下次保存将删除现有的高级 parameters / inputSchema，并仅保留你在表格中配置的 definition.parameters。此操作不可撤销。",
                                                okText: "确认切换",
                                                cancelText: "取消",
                                                onOk: () => {
                                                    form.setFieldsValue({
                                                        httpParametersAdvancedPreserve: false,
                                                        httpParameterRows: [defaultHttpParameterRow()],
                                                    });
                                                    message.success("已切换为表单模式，请填写入参后再保存");
                                                },
                                            });
                                        }}
                                    >
                                        改为表单配置入参…
                                    </Button>
                                </Space>
                            </>
                        ) : (
                            <>
                                <Alert
                                    type="info"
                                    showIcon
                                    title="模型入参（parameters）"
                                    description={
                                        <>
                                            此处定义模型调用工具时可见的<strong>参数名、类型、是否必填与说明</strong>，会写入{" "}
                                            <Typography.Text code>definition.parameters</Typography.Text>
                                            （JSON Schema 子集）。参数名须与 URL 中{" "}
                                            <Typography.Text code>&#123;参数名&#125;</Typography.Text> 一致。
                                            {showHttpBodySwitch ? (
                                                <>
                                                    {" "}
                                                    开启「发送 JSON 请求体」时，POST/PUT/PATCH
                                                    会把<strong>整份入参对象</strong>
                                                    序列化为 body。
                                                </>
                                            ) : null}
                                        </>
                                    }
                                    style={{marginBottom: 16}}
                                />
                                <Form.Item shouldUpdate noStyle>
                                    {() => {
                                        const url = String(form.getFieldValue("httpUrl") ?? "");
                                        const rows = form.getFieldValue("httpParameterRows") as
                                            | ToolFormValues["httpParameterRows"]
                                            | undefined;
                                        const ph = extractHttpUrlPlaceholderNames(url);
                                        if (ph.length === 0) {
                                            return null;
                                        }
                                        const declared = new Set(
                                            (rows ?? [])
                                                .map((r) => (r?.paramName ?? "").trim())
                                                .filter(Boolean),
                                        );
                                        const missing = ph.filter((p) => !declared.has(p));
                                        if (missing.length > 0) {
                                            return (
                                                <Typography.Paragraph type="warning" style={{marginBottom: 8}}>
                                                    URL 中仍有占位符未在下方声明同名入参：
                                                    {missing.map((p, i) => (
                                                        <React.Fragment key={p}>
                                                            {i > 0 ? "、" : " "}
                                                            <Typography.Text code>{`{${p}}`}</Typography.Text>
                                                        </React.Fragment>
                                                    ))}
                                                    。保存将失败，请补全或修改 URL。
                                                </Typography.Paragraph>
                                            );
                                        }
                                        return (
                                            <Typography.Paragraph type="success" style={{marginBottom: 8}}>
                                                URL 占位符与已填写的入参名称一致。
                                            </Typography.Paragraph>
                                        );
                                    }}
                                </Form.Item>
                                <Form.Item label="调用参数（建议与 URL 占位符一致）">
                                    <Form.List name="httpParameterRows">
                                        {(fields, {add, remove}) => (
                                            <>
                                                {fields.map(({key, name, ...restField}) => (
                                                    <Space
                                                        key={key}
                                                        style={{display: "flex", marginBottom: 8, flexWrap: "wrap"}}
                                                        align="start"
                                                    >
                                                        <Form.Item
                                                            {...restField}
                                                            name={[name, "paramName"]}
                                                            style={{width: 140, marginBottom: 0}}
                                                        >
                                                            <Input placeholder="参数名"/>
                                                        </Form.Item>
                                                        <Form.Item
                                                            {...restField}
                                                            name={[name, "paramType"]}
                                                            style={{width: 112, marginBottom: 0}}
                                                        >
                                                            <Select options={HTTP_PARAM_TYPE_OPTIONS}/>
                                                        </Form.Item>
                                                        <Form.Item
                                                            {...restField}
                                                            name={[name, "required"]}
                                                            valuePropName="checked"
                                                            style={{marginBottom: 0, lineHeight: "32px"}}
                                                        >
                                                            <Checkbox>必填</Checkbox>
                                                        </Form.Item>
                                                        <Form.Item
                                                            {...restField}
                                                            name={[name, "paramDescription"]}
                                                            style={{flex: 1, minWidth: 160, marginBottom: 0}}
                                                        >
                                                            <Input placeholder="给模型看的说明（可选）"/>
                                                        </Form.Item>
                                                        <MinusCircleOutlined
                                                            style={{marginTop: 8}}
                                                            onClick={() => remove(name)}
                                                        />
                                                    </Space>
                                                ))}
                                                <Form.Item style={{marginBottom: 0}}>
                                                    <Button
                                                        type="dashed"
                                                        onClick={() => add(defaultHttpParameterRow())}
                                                        block
                                                        icon={<PlusOutlined/>}
                                                    >
                                                        添加入参
                                                    </Button>
                                                </Form.Item>
                                            </>
                                        )}
                                    </Form.List>
                                </Form.Item>
                            </>
                        )}
                        {watchedPreserveHttpOutput ? (
                            <>
                                <Alert
                                    type="warning"
                                    showIcon
                                    title="高级出参已保留"
                                    description={
                                        <>
                                            <Typography.Text code>definition.outputSchema</Typography.Text>{" "}
                                            存在无法用表格无损表达的结构。保存
                                            URL、入参、请求头等时<strong>不会覆盖</strong>
                                            出参定义；完整内容见工具详情。
                                        </>
                                    }
                                    style={{marginBottom: 12}}
                                />
                                <Button
                                    type="primary"
                                    ghost
                                    style={{marginBottom: 16}}
                                    onClick={() => {
                                        Modal.confirm({
                                            title: "改为表单配置出参？",
                                            content:
                                                "确认后，下次保存将删除现有高级 outputSchema，仅保留表格生成的 definition.outputSchema。",
                                            okText: "确认切换",
                                            cancelText: "取消",
                                            onOk: () => {
                                                form.setFieldsValue({
                                                    httpOutputSchemaAdvancedPreserve: false,
                                                    httpOutputParameterRows: [defaultHttpParameterRow()],
                                                });
                                                message.success("已切换为表单模式，请填写出参后再保存");
                                            },
                                        });
                                    }}
                                >
                                    改为表单配置出参…
                                </Button>
                            </>
                        ) : (
                            <>
                                <Alert
                                    type="info"
                                    showIcon
                                    title="返回 / 出参（outputSchema）"
                                    description={
                                        <>
                                            描述<strong>响应体</strong>（多为 JSON 文本）里有哪些字段，写入{" "}
                                            <Typography.Text code>definition.outputSchema</Typography.Text>。
                                            运行时<strong>不校验</strong>真实 HTTP 响应；后端会把说明合并进工具
                                            description，供模型理解接口返回含义。
                                        </>
                                    }
                                    style={{marginBottom: 16}}
                                />
                                <Form.Item label="出参字段（可选）">
                                    <Form.List name="httpOutputParameterRows">
                                        {(fields, {add, remove}) => (
                                            <>
                                                {fields.map(({key, name, ...restField}) => (
                                                    <Space
                                                        key={key}
                                                        style={{display: "flex", marginBottom: 8, flexWrap: "wrap"}}
                                                        align="start"
                                                    >
                                                        <Form.Item
                                                            {...restField}
                                                            name={[name, "paramName"]}
                                                            style={{width: 140, marginBottom: 0}}
                                                        >
                                                            <Input placeholder="字段名"/>
                                                        </Form.Item>
                                                        <Form.Item
                                                            {...restField}
                                                            name={[name, "paramType"]}
                                                            style={{width: 112, marginBottom: 0}}
                                                        >
                                                            <Select options={HTTP_PARAM_TYPE_OPTIONS}/>
                                                        </Form.Item>
                                                        <Form.Item
                                                            {...restField}
                                                            name={[name, "required"]}
                                                            valuePropName="checked"
                                                            style={{marginBottom: 0, lineHeight: "32px"}}
                                                        >
                                                            <Checkbox>响应中必有</Checkbox>
                                                        </Form.Item>
                                                        <Form.Item
                                                            {...restField}
                                                            name={[name, "paramDescription"]}
                                                            style={{flex: 1, minWidth: 160, marginBottom: 0}}
                                                        >
                                                            <Input placeholder="字段含义（可选）"/>
                                                        </Form.Item>
                                                        <MinusCircleOutlined
                                                            style={{marginTop: 8}}
                                                            onClick={() => remove(name)}
                                                        />
                                                    </Space>
                                                ))}
                                                <Form.Item style={{marginBottom: 0}}>
                                                    <Button
                                                        type="dashed"
                                                        onClick={() => add(defaultHttpParameterRow())}
                                                        block
                                                        icon={<PlusOutlined/>}
                                                    >
                                                        添加出参字段
                                                    </Button>
                                                </Form.Item>
                                            </>
                                        )}
                                    </Form.List>
                                </Form.Item>
                            </>
                        )}
                        {showHttpBodySwitch ? (
                            <Form.Item
                                name="sendJsonBody"
                                label="发送 JSON 请求体"
                                valuePropName="checked"
                                extra="默认开启：会把模型传入的整份入参对象序列化为 JSON 作为请求体。关闭后仅做 URL 占位符替换，不再附带 body（适用于纯 query/路径 API）。"
                            >
                                <Switch/>
                            </Form.Item>
                        ) : null}
                        <Form.Item label="请求头（可选）">
                            <Typography.Paragraph type="secondary" style={{marginTop: 0, marginBottom: 8}}>
                                逐行填写名称与取值，无需手写 JSON。
                            </Typography.Paragraph>
                            <Form.List name="httpHeaderRows">
                                {(fields, {add, remove}) => (
                                    <>
                                        {fields.map(({key, name, ...restField}) => (
                                            <Space
                                                key={key}
                                                style={{display: "flex", marginBottom: 8}}
                                                align="baseline"
                                            >
                                                <Form.Item
                                                    {...restField}
                                                    name={[name, "headerName"]}
                                                    rules={[]}
                                                    style={{flex: 1, marginBottom: 0}}
                                                >
                                                    <Input placeholder="Header 名称，如 Accept"/>
                                                </Form.Item>
                                                <Form.Item
                                                    {...restField}
                                                    name={[name, "value"]}
                                                    style={{flex: 1, marginBottom: 0}}
                                                >
                                                    <Input placeholder="取值"/>
                                                </Form.Item>
                                                <MinusCircleOutlined onClick={() => remove(name)}/>
                                            </Space>
                                        ))}
                                        <Form.Item style={{marginBottom: 0}}>
                                            <Button type="dashed" onClick={() => add({headerName: "", value: ""})} block
                                                    icon={<PlusOutlined/>}>
                                                添加请求头
                                            </Button>
                                        </Form.Item>
                                    </>
                                )}
                            </Form.List>
                        </Form.Item>
                    </>
                ) : null}

                {watchedToolType === "MCP" ? (
                    <>
                        <Form.Item name="mcpParametersAdvancedPreserve" valuePropName="checked" hidden>
                            <Checkbox/>
                        </Form.Item>
                        <Alert
                            type="info"
                            showIcon
                            title="MCP（外部 Server）"
                            description={
                                <>
                                    填写远端 MCP 的 <Typography.Text code>SSE</Typography.Text> 根地址（完整
                                    URL，与对端文档一致；部分实现为{" "}
                                    <Typography.Text code>/sse</Typography.Text>，本服务默认在{" "}
                                    <Typography.Text code>/mcp</Typography.Text>）。本服务自身也会对外暴露 MCP（见后端{" "}
                                    <Typography.Text code>agentlego.mcp.server.sse-path</Typography.Text>
                                    ），供外部客户端连接并调用与 LOCAL 内置一致的工具列表。下方可配置{" "}
                                    <Typography.Text code>definition.parameters</Typography.Text>，用于模型侧与联调表单的参数说明。
                                </>
                            }
                            style={{marginBottom: 16}}
                        />
                        <Form.Item name="name" label="平台工具名（name）" rules={NAME_ID_RULES}>
                            <Input placeholder="智能体里看到的名字；若与远端工具名不同，请填写下方「远端工具名」"/>
                        </Form.Item>
                        <Form.Item
                            name="mcpEndpoint"
                            label="远端 SSE 端点"
                            rules={[{required: true, message: "请填写外部 MCP Server 的 SSE URL"}]}
                        >
                            <Input placeholder="例如 http://127.0.0.1:3000/sse"/>
                        </Form.Item>
                        <Form.Item label=" ">
                            <Button
                                type="link"
                                style={{padding: 0}}
                                onClick={() => {
                                    const ep = form.getFieldValue("mcpEndpoint") as string | undefined;
                                    setMcpBulkDefaultEndpoint(ep?.trim() || undefined);
                                    setMcpBulkOpen(true);
                                }}
                            >
                                从该 SSE 地址发现并批量导入远端工具…
                            </Button>
                        </Form.Item>
                        <Form.Item name="mcpRemoteToolName" label="远端工具名（可选）">
                            <Input placeholder="省略则与「平台工具名」相同，并用于 tools/call"/>
                        </Form.Item>
                        {watchedPreserveMcpParams ? (
                            <>
                                <Alert
                                    type="warning"
                                    showIcon
                                    title="高级入参已保留"
                                    description={
                                        <>
                                            当前 <Typography.Text code>parameters</Typography.Text> /{" "}
                                            <Typography.Text code>inputSchema</Typography.Text>{" "}
                                            无法用表格无损表达。你可继续改端点、工具名与说明；<strong>保存不会覆盖</strong>
                                            上述入参字段。完整内容见工具详情「definition」或 API。
                                        </>
                                    }
                                    style={{marginBottom: 12}}
                                />
                                <Button
                                    type="primary"
                                    ghost
                                    onClick={() => {
                                        Modal.confirm({
                                            title: "改为表单配置 MCP 入参？",
                                            content:
                                                "确认后，下次保存将删除现有的高级 parameters / inputSchema，并仅保留表格中的 definition.parameters。此操作不可撤销。",
                                            okText: "确认切换",
                                            cancelText: "取消",
                                            onOk: () => {
                                                form.setFieldsValue({
                                                    mcpParametersAdvancedPreserve: false,
                                                    mcpParameterRows: [defaultHttpParameterRow()],
                                                });
                                                message.success("已切换为表单模式，请填写入参后再保存");
                                            },
                                        });
                                    }}
                                >
                                    改为表单配置入参…
                                </Button>
                            </>
                        ) : (
                            <>
                                <Alert
                                    type="info"
                                    showIcon
                                    title="调用入参 Schema（可选）"
                                    description={
                                        <>
                                            写入 <Typography.Text code>definition.parameters</Typography.Text>
                                            ，与 HTTP 工具相同为 JSON Schema
                                            子集；用于智能体侧工具描述与详情页展示。不填则运行时由后端按远端{" "}
                                            <Typography.Text code>list_tools</Typography.Text> 推断或使用宽松 object。
                                        </>
                                    }
                                    style={{marginBottom: 12}}
                                />
                                <Form.Item label="参数表（parameters）">
                                    <Form.List name="mcpParameterRows">
                                        {(fields, {add, remove}) => (
                                            <>
                                                {fields.map(({key, name, ...restField}) => (
                                                    <Space
                                                        key={key}
                                                        style={{display: "flex", marginBottom: 8, flexWrap: "wrap"}}
                                                        align="start"
                                                    >
                                                        <Form.Item
                                                            {...restField}
                                                            name={[name, "paramName"]}
                                                            style={{width: 140, marginBottom: 0}}
                                                        >
                                                            <Input placeholder="参数名"/>
                                                        </Form.Item>
                                                        <Form.Item
                                                            {...restField}
                                                            name={[name, "paramType"]}
                                                            style={{width: 112, marginBottom: 0}}
                                                        >
                                                            <Select options={HTTP_PARAM_TYPE_OPTIONS}/>
                                                        </Form.Item>
                                                        <Form.Item
                                                            {...restField}
                                                            name={[name, "required"]}
                                                            valuePropName="checked"
                                                            style={{marginBottom: 0, lineHeight: "32px"}}
                                                        >
                                                            <Checkbox>必填</Checkbox>
                                                        </Form.Item>
                                                        <Form.Item
                                                            {...restField}
                                                            name={[name, "paramDescription"]}
                                                            style={{flex: 1, minWidth: 160, marginBottom: 0}}
                                                        >
                                                            <Input placeholder="说明"/>
                                                        </Form.Item>
                                                        <MinusCircleOutlined
                                                            style={{marginTop: 8}}
                                                            onClick={() => remove(name)}
                                                        />
                                                    </Space>
                                                ))}
                                                <Form.Item style={{marginBottom: 0}}>
                                                    <Button
                                                        type="dashed"
                                                        onClick={() => add(defaultHttpParameterRow())}
                                                        block
                                                        icon={<PlusOutlined/>}
                                                    >
                                                        添加参数
                                                    </Button>
                                                </Form.Item>
                                            </>
                                        )}
                                    </Form.List>
                                </Form.Item>
                            </>
                        )}
                    </>
                ) : null}

                {watchedToolType === "WORKFLOW" ? (
                    <>
                        <Form.Item name="name" label="工具名称（name）" rules={NAME_ID_RULES}>
                            <Input/>
                        </Form.Item>
                        <Form.Item name="workflowId" label="工作流 ID" rules={[{required: true, message: "必填"}]}>
                            <Input placeholder="平台工作流 Snowflake ID"/>
                        </Form.Item>
                    </>
                ) : null}

                <Form.Item name="toolDescription" label="说明（可选，写入 definition.description）">
                    <Input.TextArea rows={3} placeholder="给模型/同事看的简短说明"/>
                </Form.Item>
            </Form>

            <McpBatchImportModal
                open={mcpBulkOpen}
                onClose={() => setMcpBulkOpen(false)}
                onSuccess={onSaved}
                defaultEndpoint={mcpBulkDefaultEndpoint}
            />
        </Drawer>
    );
}
