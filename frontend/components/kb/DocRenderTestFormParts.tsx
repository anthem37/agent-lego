"use client";

import {Form, Select} from "antd";
import type {FormInstance} from "antd/es/form";
import React from "react";

import type {DocRenderTestForm} from "@/lib/kb/doc-render-test";
import {buildRenderTestInputFieldOptions, buildRenderTestOutputFieldOptions,} from "@/lib/kb/render-test-field-options";
import type {KbDocumentDto} from "@/lib/kb/types";
import type {ToolDto} from "@/lib/tools/types";

/** 渲染测试：出参字段路径（文档绑定 + 工具 outputSchema 合并） */
export function DocRenderTestOutputKeySelect(props: {
    form: FormInstance<DocRenderTestForm>;
    blockIndex: number;
    pairFieldName: number;
    doc: KbDocumentDto | null;
    toolById: Record<string, ToolDto>;
}) {
    const toolId = Form.useWatch(["toolBlocks", props.blockIndex, "toolId"], props.form) ?? "";
    const tool = props.toolById[toolId];
    const keyVal = Form.useWatch(
        ["toolBlocks", props.blockIndex, "pairs", props.pairFieldName, "key"],
        props.form,
    );
    const options = React.useMemo(
        () => buildRenderTestOutputFieldOptions(props.doc, toolId, tool),
        [props.doc, toolId, tool],
    );
    const merged = React.useMemo(() => {
        const o = [...options];
        if (keyVal && !o.some((x) => x.value === keyVal)) {
            o.unshift({value: keyVal, label: `${keyVal}（已保存）`});
        }
        return o;
    }, [options, keyVal]);

    return (
        <Form.Item
            name={[props.pairFieldName, "key"]}
            noStyle
            rules={[{required: true, message: "请选择出参字段"}]}
        >
            <Select
                showSearch
                optionFilterProp="label"
                placeholder={toolId ? "选择出参字段" : "请先选择工具"}
                disabled={!toolId}
                options={merged}
                allowClear
                style={{minWidth: 240, flex: "1 1 200px"}}
            />
        </Form.Item>
    );
}

/** 渲染测试：入参字段（仅工具 parameters / inputSchema，不参与渲染 API） */
export function DocRenderTestInputKeySelect(props: {
    form: FormInstance<DocRenderTestForm>;
    blockIndex: number;
    pairFieldName: number;
    toolById: Record<string, ToolDto>;
}) {
    const toolId = Form.useWatch(["toolBlocks", props.blockIndex, "toolId"], props.form) ?? "";
    const tool = props.toolById[toolId];
    const keyVal = Form.useWatch(
        ["toolBlocks", props.blockIndex, "inputPairs", props.pairFieldName, "key"],
        props.form,
    );
    const options = React.useMemo(() => buildRenderTestInputFieldOptions(tool), [tool]);
    const merged = React.useMemo(() => {
        const o = [...options];
        if (keyVal && !o.some((x) => x.value === keyVal)) {
            o.unshift({value: keyVal, label: `${keyVal}（已保存）`});
        }
        return o;
    }, [options, keyVal]);

    return (
        <Form.Item name={[props.pairFieldName, "key"]} noStyle>
            <Select
                showSearch
                optionFilterProp="label"
                placeholder={toolId ? "选择入参字段" : "请先选择工具"}
                disabled={!toolId}
                options={merged}
                allowClear
                style={{minWidth: 240, flex: "1 1 200px"}}
            />
        </Form.Item>
    );
}
