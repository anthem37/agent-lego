import type {default as DeltaType} from "quill-delta";
import Quill from "quill";

import {formatKbToolFieldChipDisplay, formatKbToolFieldPathForTitle,} from "@/lib/kb/kb-rich-tag-labels";

const Embed = Quill.import("blots/embed") as typeof import("quill/blots/embed").default;
const Delta = Quill.import("delta") as typeof DeltaType;

/** Quill 内嵌格式名 */
export const KB_KNOWLEDGE_TAG_BLOT = "kbKnowledgeTag";

export type KbTagValue =
    | { kind: "tool"; code: string; /** 编辑器展示用，不入库业务键 */ displayText?: string }
    | {
    kind: "tool_field";
    code: string;
    field: string;
    displayText?: string;
    /** 出参表字段说明，写入 data-kb-field-desc 便于详情回显 */
    fieldDescription?: string;
};

let registered = false;

function labelSpan(text: string): HTMLSpanElement {
    const s = document.createElement("span");
    s.className = "kb-knowledge-inline__text";
    s.textContent = text;
    return s;
}

/**
 * 知识库富文本标签（类链接展示）：
 * - data-type=tool + data-tool-code
 * - data-type=tool_field + data-tool-code + data-tool-field（点分路径，无 $. 前缀）
 */
export function registerKbKnowledgeQuillFormats(): void {
    if (registered) {
        return;
    }
    registered = true;

    class KbKnowledgeTagBlot extends Embed {
        static override blotName = KB_KNOWLEDGE_TAG_BLOT;
        static override tagName = "span";
        static override className = "kb-knowledge-inline";

        static override create(value: KbTagValue) {
            const node = super.create() as HTMLSpanElement;
            if (value.kind === "tool") {
                node.setAttribute("data-type", "tool");
                node.setAttribute("data-tool-code", value.code);
                const display = value.displayText?.trim() || value.code;
                node.setAttribute("data-kb-display", display);
                node.title = `工具标识：${value.code}`;
                node.appendChild(labelSpan(display));
            } else {
                node.setAttribute("data-type", "tool_field");
                node.setAttribute("data-tool-code", value.code);
                node.setAttribute("data-tool-field", value.field);
                const fd = value.fieldDescription?.trim();
                if (fd) {
                    node.setAttribute("data-kb-field-desc", fd);
                }
                const display =
                    value.displayText?.trim() || formatKbToolFieldChipDisplay(value.code, value.field, fd);
                node.setAttribute("data-kb-display", display);
                node.title = `工具「${value.code}」· 出参路径：${formatKbToolFieldPathForTitle(value.field)}`;
                node.appendChild(labelSpan(display));
            }
            return node;
        }

        static override value(node: HTMLElement): KbTagValue {
            const code = (node.getAttribute("data-tool-code") ?? node.getAttribute("tool-code") ?? "").trim();
            const field = (node.getAttribute("data-tool-field") ?? node.getAttribute("tool-field") ?? "").trim();
            const displayText = node.getAttribute("data-kb-display")?.trim() || undefined;
            if (field) {
                const fieldDescription = node.getAttribute("data-kb-field-desc")?.trim() || undefined;
                return {
                    kind: "tool_field",
                    code,
                    field,
                    ...(displayText ? {displayText} : {}),
                    ...(fieldDescription ? {fieldDescription} : {}),
                };
            }
            return {kind: "tool", code, ...(displayText ? {displayText} : {})};
        }
    }

    Quill.register(KbKnowledgeTagBlot, true);
}

export function kbKnowledgeTagClipboardMatchers(): Array<[string, (node: Node, delta: DeltaType) => DeltaType]> {
    return [
        [
            "SPAN",
            (node, delta) => {
                const el = node as HTMLElement;
                if (!el.getAttribute) {
                    return delta;
                }
                const type = (el.getAttribute("data-type") ?? "").toLowerCase();
                const code = (el.getAttribute("data-tool-code") ?? el.getAttribute("tool-code") ?? "").trim();
                const field = (el.getAttribute("data-tool-field") ?? el.getAttribute("tool-field") ?? "").trim();
                const disp = el.getAttribute("data-kb-display")?.trim();
                if (field && code) {
                    const fd = el.getAttribute("data-kb-field-desc")?.trim();
                    return new Delta().insert({
                        [KB_KNOWLEDGE_TAG_BLOT]: {
                            kind: "tool_field",
                            code,
                            field,
                            ...(disp ? {displayText: disp} : {}),
                            ...(fd ? {fieldDescription: fd} : {}),
                        },
                    });
                }
                if (type === "tool" && code) {
                    return new Delta().insert({
                        [KB_KNOWLEDGE_TAG_BLOT]: {
                            kind: "tool",
                            code,
                            ...(disp ? {displayText: disp} : {}),
                        },
                    });
                }
                return delta;
            },
        ],
    ];
}
