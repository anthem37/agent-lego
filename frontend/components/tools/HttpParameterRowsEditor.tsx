"use client";

import {MinusCircleOutlined, PlusOutlined} from "@ant-design/icons";
import {Button, Checkbox, Form, Input, Select, Space, Typography} from "antd";
import type {FormInstance} from "antd/es/form";
import type {FormListFieldData} from "antd/es/form/FormList";
import type {NamePath} from "antd/es/form/interface";
import React from "react";

import {
    defaultHttpParameterRow,
    HTTP_ARRAY_ITEM_TYPE_OPTIONS,
    HTTP_PARAM_TYPE_OPTIONS,
    MAX_NESTED_HTTP_PARAM_DEPTH,
} from "@/lib/tools/form";
import type {HttpArrayItemPrimitiveType, HttpParamType} from "@/lib/tools/types";

/** 表单 store 中指向「当前 Form.List 绑定的数组」的完整路径 */
export type HttpParameterListStorePath = (string | number)[];

export type HttpParameterRowsEditorProps = {
    /**
     * 传给 Form.List 的 name：根级为字段名（如 "httpParameterRows"）；
     * 嵌套时为相对路径，如 [rowIndex, "children"]。
     */
    listName: NamePath;
    /**
     * 当前 Form.List 在表单 store 中的完整路径（与 listName 解析后的位置一致），例如：
     * ['httpParameterRows']、['httpParameterRows',0,'children']。
     */
    storePathToList: HttpParameterListStorePath;
    depth?: number;
    addButtonLabel: string;
    requiredCheckboxLabel: string;
    namePlaceholder: string;
    descriptionPlaceholder?: string;
    nestedAddLabels?: { objectChildren: string; arrayItemObject: string };
};

type RowFieldsProps = {
    restField: Omit<FormListFieldData, "key" | "name">;
    storePathToList: HttpParameterListStorePath;
    rowIndex: number;
    depth: number;
    canNest: boolean;
    typeOptions: typeof HTTP_PARAM_TYPE_OPTIONS;
    onRemove: () => void;
    form: FormInstance;
    requiredCheckboxLabel: string;
    namePlaceholder: string;
    descriptionPlaceholder: string;
    nestedAddLabels: { objectChildren: string; arrayItemObject: string };
};

function ParameterRowFields({
                                restField,
                                storePathToList,
                                rowIndex,
                                depth,
                                canNest,
                                typeOptions,
                                onRemove,
                                form,
                                requiredCheckboxLabel,
                                namePlaceholder,
                                descriptionPlaceholder,
                                nestedAddLabels,
                            }: RowFieldsProps) {
    const rowFullPath = React.useMemo(
        () => [...storePathToList, rowIndex] as const,
        [storePathToList, rowIndex],
    );

    const paramType = Form.useWatch([...rowFullPath, "paramType"], form) as HttpParamType | undefined;
    const arrayItemPrim = Form.useWatch(
        [...rowFullPath, "arrayItemsPrimitiveType"],
        form,
    ) as HttpArrayItemPrimitiveType | undefined;

    function handleParamTypeChange(v: HttpParamType) {
        const base = [...rowFullPath];
        if (v !== "object") {
            form.setFieldValue([...base, "children"], []);
        }
        if (v !== "array") {
            form.setFieldValue([...base, "arrayItemsPrimitiveType"], undefined);
            form.setFieldValue([...base, "arrayItemProperties"], []);
        } else {
            const cur = form.getFieldValue([...base, "arrayItemsPrimitiveType"]);
            if (cur == null) {
                form.setFieldValue([...base, "arrayItemsPrimitiveType"], "string");
            }
        }
        if (v === "object") {
            const ch = form.getFieldValue([...base, "children"]);
            if (ch == null || !Array.isArray(ch)) {
                form.setFieldValue([...base, "children"], []);
            }
        }
    }

    function handleArrayItemTypeChange(v: HttpArrayItemPrimitiveType) {
        const base = [...rowFullPath];
        if (v !== "object") {
            form.setFieldValue([...base, "arrayItemProperties"], []);
        } else {
            const x = form.getFieldValue([...base, "arrayItemProperties"]);
            if (x == null || !Array.isArray(x)) {
                form.setFieldValue([...base, "arrayItemProperties"], []);
            }
        }
    }

    const isObject = paramType === "object";
    const isArray = paramType === "array";
    const showArrayObjectProps = isArray && (arrayItemPrim ?? "string") === "object";

    const childListPath = React.useMemo(
        () => [...rowFullPath, "children"] as HttpParameterListStorePath,
        [rowFullPath],
    );
    const arrayItemListPath = React.useMemo(
        () => [...rowFullPath, "arrayItemProperties"] as HttpParameterListStorePath,
        [rowFullPath],
    );

    return (
        <div style={{marginBottom: 12}}>
            <Space style={{display: "flex", flexWrap: "wrap", alignItems: "flex-start"}} align="start">
                <Form.Item {...restField} name={[rowIndex, "paramName"]} style={{width: 140, marginBottom: 0}}>
                    <Input placeholder={namePlaceholder}/>
                </Form.Item>
                <Form.Item {...restField} name={[rowIndex, "paramType"]} style={{width: 128, marginBottom: 0}}>
                    <Select
                        options={typeOptions}
                        onChange={(v) => handleParamTypeChange(v as HttpParamType)}
                    />
                </Form.Item>
                <Form.Item
                    {...restField}
                    name={[rowIndex, "required"]}
                    valuePropName="checked"
                    initialValue={false}
                    style={{marginBottom: 0, lineHeight: "32px"}}
                >
                    <Checkbox>{requiredCheckboxLabel}</Checkbox>
                </Form.Item>
                <Form.Item
                    {...restField}
                    name={[rowIndex, "paramDescription"]}
                    style={{flex: 1, minWidth: 160, marginBottom: 0}}
                >
                    <Input placeholder={descriptionPlaceholder}/>
                </Form.Item>
                <MinusCircleOutlined style={{marginTop: 8}} onClick={onRemove}/>
            </Space>
            {isArray ? (
                <div style={{marginTop: 8, marginLeft: 16, paddingLeft: 12, borderLeft: "2px solid rgba(0,0,0,0.06)"}}>
                    <Typography.Text type="secondary" style={{fontSize: 12, marginRight: 8}}>
                        数组元素类型
                    </Typography.Text>
                    <Form.Item
                        {...restField}
                        name={[rowIndex, "arrayItemsPrimitiveType"]}
                        initialValue="string"
                        style={{display: "inline-block", width: 200, marginBottom: 0}}
                    >
                        <Select
                            options={
                                canNest
                                    ? HTTP_ARRAY_ITEM_TYPE_OPTIONS
                                    : HTTP_ARRAY_ITEM_TYPE_OPTIONS.filter((o) => o.value !== "object")
                            }
                            onChange={(v) => handleArrayItemTypeChange(v as HttpArrayItemPrimitiveType)}
                        />
                    </Form.Item>
                </div>
            ) : null}
            {isObject ? (
                <div style={{marginTop: 8, marginLeft: 16, paddingLeft: 12, borderLeft: "2px solid rgba(0,0,0,0.06)"}}>
                    <Typography.Text type="secondary" style={{display: "block", marginBottom: 8}}>
                        对象属性
                    </Typography.Text>
                    <HttpParameterRowsEditor
                        listName={[rowIndex, "children"]}
                        storePathToList={childListPath}
                        depth={depth + 1}
                        addButtonLabel={nestedAddLabels.objectChildren}
                        requiredCheckboxLabel={requiredCheckboxLabel}
                        namePlaceholder={namePlaceholder}
                        descriptionPlaceholder={descriptionPlaceholder}
                        nestedAddLabels={nestedAddLabels}
                    />
                </div>
            ) : null}
            {showArrayObjectProps ? (
                <div style={{marginTop: 8, marginLeft: 16, paddingLeft: 12, borderLeft: "2px solid rgba(0,0,0,0.06)"}}>
                    <Typography.Text type="secondary" style={{display: "block", marginBottom: 8}}>
                        数组元素（对象）的属性
                    </Typography.Text>
                    <HttpParameterRowsEditor
                        listName={[rowIndex, "arrayItemProperties"]}
                        storePathToList={arrayItemListPath}
                        depth={depth + 1}
                        addButtonLabel={nestedAddLabels.arrayItemObject}
                        requiredCheckboxLabel={requiredCheckboxLabel}
                        namePlaceholder={namePlaceholder}
                        descriptionPlaceholder={descriptionPlaceholder}
                        nestedAddLabels={nestedAddLabels}
                    />
                </div>
            ) : null}
        </div>
    );
}

export function HttpParameterRowsEditor(props: HttpParameterRowsEditorProps) {
    const {
        listName,
        storePathToList,
        depth = 0,
        addButtonLabel,
        requiredCheckboxLabel,
        namePlaceholder,
        descriptionPlaceholder = "说明（可选）",
        nestedAddLabels = {
            objectChildren: "添加嵌套属性",
            arrayItemObject: "添加元素字段",
        },
    } = props;

    const form = Form.useFormInstance();
    const canNest = depth < MAX_NESTED_HTTP_PARAM_DEPTH;
    const typeOptions = React.useMemo(
        () =>
            canNest
                ? HTTP_PARAM_TYPE_OPTIONS
                : HTTP_PARAM_TYPE_OPTIONS.filter((o) => o.value !== "object" && o.value !== "array"),
        [canNest],
    );

    return (
        <Form.List name={listName}>
            {(fields, {add, remove}) => (
                <>
                    {fields.map(({key, name: rowIdx, ...restField}) => (
                        <ParameterRowFields
                            key={key}
                            restField={restField}
                            storePathToList={storePathToList}
                            rowIndex={rowIdx}
                            depth={depth}
                            canNest={canNest}
                            typeOptions={typeOptions}
                            onRemove={() => remove(rowIdx)}
                            form={form}
                            requiredCheckboxLabel={requiredCheckboxLabel}
                            namePlaceholder={namePlaceholder}
                            descriptionPlaceholder={descriptionPlaceholder}
                            nestedAddLabels={nestedAddLabels}
                        />
                    ))}
                    <Form.Item style={{marginBottom: 0}}>
                        <Button
                            type="dashed"
                            onClick={() => add(defaultHttpParameterRow())}
                            block
                            icon={<PlusOutlined/>}
                        >
                            {addButtonLabel}
                        </Button>
                    </Form.Item>
                </>
            )}
        </Form.List>
    );
}
