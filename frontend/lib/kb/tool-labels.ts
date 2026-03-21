import type {ToolDto} from "@/lib/tools/types";
import {toolTypeDisplayName} from "@/lib/tool-labels";

/** 下拉/表格主文案：展示名优先，否则 name */
export function kbToolPrimaryLabel(t: ToolDto): string {
    const d = t.displayLabel?.trim();
    return d ? `${d}（${t.name}）` : t.name;
}

/**
 * 富文本内嵌「工具」标签可见文案：只要展示名（中文），不要「中文（英文）」；
 * 无展示名时退回运行时 name。
 */
export function kbToolRichTagLabel(t: ToolDto): string {
    const d = t.displayLabel?.trim();
    return d || (t.name?.trim() || t.id);
}

/** 搜索用：名称、id、类型、说明 */
export function kbToolSearchBlob(t: ToolDto): string {
    return `${t.name} ${t.id} ${t.displayLabel ?? ""} ${t.description ?? ""} ${t.toolType} ${t.toolCategory ?? ""}`.toLowerCase();
}

/** 多选 Tag 内短文案 */
export function kbToolChipText(t: ToolDto, maxLen = 22): string {
    const raw = t.displayLabel?.trim() || t.name || t.id;
    if (raw.length <= maxLen) {
        return raw;
    }
    return `${raw.slice(0, maxLen - 1)}…`;
}

/** 下拉项副标题：类型 + id 前缀 */
export function kbToolOptionDescription(t: ToolDto): string {
    return `${toolTypeDisplayName(t.toolType)} · ${t.id.slice(0, 12)}…`;
}
