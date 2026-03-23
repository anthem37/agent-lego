"use client";

import {Space, Table, Typography} from "antd";
import type {ColumnsType} from "antd/es/table";

import {renderJsonSchemaPropertyTable} from "@/components/tools/JsonSchemaPropertyTable";
import type {LocalBuiltinParamMetaDto, LocalBuiltinToolMetaDto} from "@/lib/tools/types";

const inputColumns: ColumnsType<LocalBuiltinParamMetaDto> = [
    {
        title: "参数名",
        dataIndex: "name",
        key: "name",
        width: 120,
        render: (v: string) => <Typography.Text code>{v}</Typography.Text>,
    },
    {title: "类型", dataIndex: "type", key: "type", width: 96},
    {
        title: "必填",
        dataIndex: "required",
        key: "required",
        width: 72,
        render: (req: boolean) => (req ? "是" : "否"),
    },
    {
        title: "说明",
        dataIndex: "description",
        key: "description",
        render: (t: string | undefined) => t?.trim() || "—",
    },
];

type Props = {
    meta: LocalBuiltinToolMetaDto | null | undefined;
    /** 外层是否包一层标题（详情页可关，抽屉内可开） */
    showTitle?: boolean;
    /**
     * 为 false 时不展示入参区块（抽屉内入参由下方表单编辑，避免与只读表重复）。
     */
    showInputSection?: boolean;
};

function hasRenderableSchema(v: unknown): boolean {
    return v != null && typeof v === "object" && !Array.isArray(v) && Object.keys(v as object).length > 0;
}

/**
 * 展示单个 LOCAL 内置工具的入参 / 出参：优先使用与 HTTP 工具一致的 JSON Schema 表格（inputSchema / outputSchema），
 * 否则回退到基于 {@code inputParameters} 的简单表格。
 */
export function LocalBuiltinIoPreview(props: Props) {
    const {meta, showTitle = true, showInputSection = true} = props;
    if (!meta) {
        return null;
    }
    const inputs = meta.inputParameters ?? [];
    const useSchema =
        hasRenderableSchema(meta.inputSchema as unknown) && hasRenderableSchema(meta.outputSchema as unknown);

    const outLine =
        meta.outputDescription?.trim() ||
        [meta.outputJavaType && `返回类型 ${meta.outputJavaType}`, meta.resultConverterClass && `转换器 ${meta.resultConverterClass}`]
            .filter(Boolean)
            .join("；") ||
        "—";

    const mainTitle = showInputSection ? "入参与出参" : "出参（内置参考）";

    return (
        <Space orientation="vertical" size={8} style={{width: "100%"}}>
            {showTitle ? <Typography.Text strong>{mainTitle}</Typography.Text> : null}
            {useSchema ? (
                <>
                    {showInputSection ? (
                        <>
                            <Typography.Text type="secondary" style={{fontSize: 12}}>
                                入参（inputSchema）
                            </Typography.Text>
                            {renderJsonSchemaPropertyTable(meta.inputSchema, "input")}
                        </>
                    ) : null}
                    <Typography.Text type="secondary" style={{fontSize: 12, marginTop: showInputSection ? 4 : 0}}>
                        出参（outputSchema）
                    </Typography.Text>
                    {renderJsonSchemaPropertyTable(meta.outputSchema, "output")}
                </>
            ) : (
                <>
                    {showInputSection ? (
                        <>
                            <Typography.Text type="secondary" style={{fontSize: 12}}>
                                入参
                            </Typography.Text>
                            {inputs.length === 0 ? (
                                <Typography.Text type="secondary">无声明参数</Typography.Text>
                            ) : (
                                <Table<LocalBuiltinParamMetaDto>
                                    size="small"
                                    pagination={false}
                                    rowKey={(r) => r.name}
                                    dataSource={inputs}
                                    columns={inputColumns}
                                />
                            )}
                        </>
                    ) : null}
                    <Typography.Text type="secondary" style={{fontSize: 12, marginTop: showInputSection ? 4 : 0}}>
                        出参
                    </Typography.Text>
                    <Typography.Paragraph type="secondary" style={{marginBottom: 0}}>
                        {outLine}
                    </Typography.Paragraph>
                </>
            )}
        </Space>
    );
}
