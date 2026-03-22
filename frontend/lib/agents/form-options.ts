/**
 * 智能体表单下拉选项与 Ant Design Select filterOption 共用逻辑。
 */

import type {KbCollectionDto} from "@/lib/kb/types";
import type {MemoryPolicyDto} from "@/lib/memory-policies/api";
import type {ToolDto} from "@/lib/tools/types";
import {toolTypeDisplayName} from "@/lib/tool-labels";

export type SearchableOption = { searchText?: string };

/** 模型 / 工具等：option.searchText 已小写归一 */
export function filterModelSelectOption(input: string, option?: SearchableOption | null): boolean {
    const st = option?.searchText ?? "";
    const q = input.trim().toLowerCase();
    return !q || st.includes(q);
}

/** 记忆策略：searchText 含多字段，大小写不敏感 */
export function filterMemoryPolicySelectOption(input: string, option?: SearchableOption | null): boolean {
    const st = option?.searchText ?? "";
    const q = input.trim().toLowerCase();
    return !q || st.toLowerCase().includes(q);
}

export function buildToolSelectOptions(tools: ToolDto[]) {
    return tools.map((t) => ({
        value: t.id,
        label: `${t.name} · ${toolTypeDisplayName(t.toolType)} · ${t.id.slice(0, 10)}…`,
    }));
}

export function buildCollectionSelectOptions(collections: KbCollectionDto[]) {
    return collections.map((c) => ({
        value: c.id,
        label: `${c.name}（${c.id.slice(0, 8)}…）`,
    }));
}

export function buildMemoryPolicySelectOptions(policies: MemoryPolicyDto[]) {
    return policies.map((p) => ({
        value: p.id,
        label: `${p.name} · ${p.ownerScope}`,
        searchText: `${p.name} ${p.ownerScope} ${p.id}`,
    }));
}
