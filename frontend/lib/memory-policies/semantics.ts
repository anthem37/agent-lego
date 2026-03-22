/** 与后端 MemoryPolicySemantic / docs/memory-strategy.md 对齐的展示文案 */

export const STRATEGY_OPTIONS = [
    {value: "EPISODIC_DIALOGUE", label: "情景对话沉淀", hint: "对话中出现的事实、偏好、约定"},
    {value: "USER_PROFILE", label: "用户画像", hint: "相对稳定、可跨会话复用"},
    {value: "TASK_CONTEXT", label: "任务上下文", hint: "任务级结论（预留扩展）"},
] as const;

export const SCOPE_KIND_OPTIONS = [
    {value: "CUSTOM_NAMESPACE", label: "自定义命名空间", hint: "owner_scope 由你约定前缀，全局唯一"},
    {value: "TENANT", label: "租户（预留）", hint: "未来与租户 ID 绑定解析"},
    {value: "USER", label: "用户（预留）", hint: "未来与用户 ID 绑定解析"},
    {value: "AGENT", label: "智能体（预留）", hint: "未来与智能体 ID 绑定解析"},
] as const;

export const RETRIEVAL_OPTIONS = [
    {value: "KEYWORD", label: "关键词（已实现）", hint: "PG ILIKE / pg_trgm"},
    {value: "VECTOR", label: "向量（未接好前降级）", hint: "需向量库与嵌入"},
    {value: "HYBRID", label: "混合（未接好前降级）", hint: "关键词 + 向量"},
] as const;

export const WRITE_MODE_OPTIONS = [
    {value: "OFF", label: "不写回", hint: "不把助手输出写入记忆条目"},
    {value: "ASSISTANT_RAW", label: "写回助手原文", hint: "与去重策略配合"},
    {value: "ASSISTANT_SUMMARY", label: "摘要后写回（占位）", hint: "当前与原文写回行为一致，待接摘要"},
] as const;

export function strategyLabel(v?: string): string {
    return STRATEGY_OPTIONS.find((o) => o.value === v)?.label ?? (v ?? "—");
}

export function retrievalLabel(v?: string): string {
    return RETRIEVAL_OPTIONS.find((o) => o.value === v)?.label ?? (v ?? "—");
}

export function writeModeLabel(v?: string): string {
    return WRITE_MODE_OPTIONS.find((o) => o.value === v)?.label ?? (v ?? "—");
}
