import {formatKbToolFieldChipDisplay, formatKbToolFieldPathForTitle} from "@/lib/kb/kb-rich-tag-labels";
import {kbToolRichTagLabel} from "@/lib/kb/tool-labels";
import {kbOutputFieldDescriptionForTool} from "@/lib/tools/http-output-field-description";
import type {ToolDto} from "@/lib/tools/types";

export type KbRichHtmlDetailNormalizeOptions = {
    /** 以工具运行时 name（与 data-tool-code 一致）→ 平台工具 */
    toolsByName: Map<string, ToolDto>;
};

/**
 * 文档详情富文本展示：统一标签可读性（与入库编辑器一致）。
 * - 若有 data-kb-display，写入 .kb-knowledge-inline__text
 * - 若无（旧数据），用已绑定工具元数据生成中文摘要并补全 data-kb-display / title
 */
export function normalizeKbRichHtmlForDetailView(
    html: string,
    options: KbRichHtmlDetailNormalizeOptions,
): string {
    const raw = (html ?? "").trim();
    if (typeof window === "undefined" || !raw) {
        return html ?? "";
    }
    const doc = new DOMParser().parseFromString(raw, "text/html");
    const seen = new Set<Element>();
    doc.body
        .querySelectorAll(
            'span[data-type="tool"], span[data-type="tool_field"], span[data-type="TOOL"], span[data-type="TOOL_FIELD"], .kb-knowledge-inline',
        )
        .forEach((el) => seen.add(el));

    for (const el of seen) {
        const code = (el.getAttribute("data-tool-code") ?? el.getAttribute("tool-code") ?? "").trim();
        const field = (el.getAttribute("data-tool-field") ?? el.getAttribute("tool-field") ?? "").trim();
        const rawType = (el.getAttribute("data-type") ?? "").toLowerCase();

        /** 有 field 即视为出参字段（兼容缺 data-type 的 HTML）；纯工具须带 tool-code */
        let type: "tool" | "tool_field" | null = null;
        if (field) {
            type = "tool_field";
        } else if (code && (rawType === "tool" || rawType === "")) {
            type = "tool";
        }
        if (type == null) {
            continue;
        }
        if (type === "tool_field" && !code) {
            continue;
        }

        el.classList.add("kb-knowledge-inline");
        if (type === "tool_field") {
            el.setAttribute("data-type", "tool_field");
        } else {
            el.setAttribute("data-type", "tool");
        }

        const t = options.toolsByName.get(code);

        /** 工具：仅展示名；字段：`工具展示名.字段说明`（优先出参表说明，其次 data-kb-field-desc，再退回字段名） */
        let display: string;
        if (type === "tool_field") {
            const toolPart = t ? kbToolRichTagLabel(t) : code;
            const attrDesc = el.getAttribute("data-kb-field-desc")?.trim() ?? "";
            const resolvedDesc = kbOutputFieldDescriptionForTool(t, field);
            const fieldDesc = resolvedDesc || attrDesc;
            display = field
                ? formatKbToolFieldChipDisplay(toolPart, field, fieldDesc || undefined)
                : (el.getAttribute("data-kb-display")?.trim() || code);
            if (fieldDesc) {
                el.setAttribute("data-kb-field-desc", fieldDesc);
            } else {
                el.removeAttribute("data-kb-field-desc");
            }
        } else {
            display = t ? kbToolRichTagLabel(t) : (el.getAttribute("data-kb-display")?.trim() || code);
        }
        el.setAttribute("data-kb-display", display);

        if (type === "tool_field") {
            el.setAttribute(
                "title",
                field ? `工具「${code}」· 出参路径：${formatKbToolFieldPathForTitle(field)}` : `工具标识：${code}`,
            );
        } else {
            el.setAttribute("title", `工具标识：${code}`);
        }

        let textEl: Element | null = null;
        for (const c of Array.from(el.children)) {
            if (c.classList.contains("kb-knowledge-inline__text")) {
                textEl = c;
                break;
            }
        }
        if (!textEl) {
            el.replaceChildren();
            const span = doc.createElement("span");
            span.className = "kb-knowledge-inline__text";
            el.appendChild(span);
            textEl = span;
        }
        textEl.textContent = display;
    }

    return doc.body.innerHTML;
}

/** 由文档已绑定工具 id 列表与详情页已拉取的 ToolDto 构建 name → ToolDto */
export function kbToolsByRuntimeName(linkedToolIds: string[] | undefined, toolById: Record<string, ToolDto>): Map<string, ToolDto> {
    const m = new Map<string, ToolDto>();
    for (const id of linkedToolIds ?? []) {
        const t = toolById[id];
        const name = t?.name?.trim();
        if (name) {
            m.set(name, t);
        }
    }
    return m;
}
