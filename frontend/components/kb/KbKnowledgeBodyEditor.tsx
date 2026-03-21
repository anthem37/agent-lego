"use client";

import {Form} from "antd";
import React from "react";

import {KbRichTextField, type KbRichTextFieldHandle, type KbToolbarExtraHandlers} from "@/components/kb/KbRichTextField";

export type KbKnowledgeBodyEditorProps = {
    editorRef?: React.Ref<KbRichTextFieldHandle>;
    minHeight?: number;
    toolbarExtras?: KbToolbarExtraHandlers;
};

/**
 * 知识入库正文：仅富文本；标签由服务端解析并生成占位绑定。
 */
export function KbKnowledgeBodyEditor({editorRef, minHeight = 400, toolbarExtras}: KbKnowledgeBodyEditorProps) {
    return (
        <Form.Item name="bodyRich" noStyle>
            <KbRichTextField ref={editorRef} minHeight={minHeight} toolbarExtras={toolbarExtras}/>
        </Form.Item>
    );
}
