"use client";

import {Space, Table, Typography} from "antd";
import type {ColumnsType} from "antd/es/table";

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
};

/**
 * 展示单个 LOCAL 内置工具的入参表与出参说明（数据来自 /tools/meta/local-builtins）。
 */
export function LocalBuiltinIoPreview(props: Props) {
    const {meta, showTitle = true} = props;
    if (!meta) {
        return null;
    }
    const inputs = meta.inputParameters ?? [];
    const outLine =
        meta.outputDescription?.trim() ||
        [meta.outputJavaType && `返回类型 ${meta.outputJavaType}`, meta.resultConverterClass && `转换器 ${meta.resultConverterClass}`]
            .filter(Boolean)
            .join("；") ||
        "—";

    return (
        <Space orientation="vertical" size={8} style={{width: "100%"}}>
            {showTitle ? <Typography.Text strong>入参与出参</Typography.Text> : null}
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
            <Typography.Text type="secondary" style={{fontSize: 12, marginTop: 4}}>
                出参
            </Typography.Text>
            <Typography.Paragraph type="secondary" style={{marginBottom: 0}}>
                {outLine}
            </Typography.Paragraph>
        </Space>
    );
}
