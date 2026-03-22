import type {KbDocumentDto} from "@/lib/kb/types";

/** 文档渲染测试表单（与知识库页「渲染测试」Tab 一致） */
export type DocRenderTestForm = {
    toolBlocks: {
        toolId: string;
        /** 出参 mock → 请求体 toolOutputs */
        pairs: { key: string; value: string }[];
        /** 入参 mock（可选，仅对照工具定义；当前渲染 API 不使用） */
        inputPairs?: { key: string; value: string }[];
    }[];
};

/**
 * 将持久化的 jsonPath（如 {@code $.data.orderNo}）转为表单键（{@code data.orderNo}），
 * 与 {@link buildToolOutputsFromBlocks} 的扁平点号路径一致。
 */
export function jsonPathToFormKey(jsonPath: string): string {
    const p = (jsonPath ?? "").trim();
    if (p.startsWith("$.")) {
        return p.slice(2);
    }
    if (p.startsWith("$")) {
        return p.slice(1);
    }
    return p;
}

/**
 * 根据当前文档的关联工具与 toolOutputBindings.mappings 生成渲染测试初始值，
 * 避免用户从零手填 UUID 与键名。
 */
export function buildDocRenderInitialFromDocument(doc: KbDocumentDto | null): DocRenderTestForm {
    const empty: DocRenderTestForm = {
        toolBlocks: [{toolId: "", pairs: [{key: "", value: ""}], inputPairs: []}],
    };
    if (!doc) {
        return empty;
    }
    const ids = doc.linkedToolIds?.filter(Boolean) ?? [];
    const bindings = doc.toolOutputBindings as
        | { mappings?: Array<{ toolId?: string; jsonPath?: string }> }
        | undefined;
    const mappings = Array.isArray(bindings?.mappings) ? bindings.mappings : [];

    if (ids.length === 0) {
        return empty;
    }

    const toolBlocks = ids.map((tid) => {
        const ms = mappings.filter((m) => m.toolId === tid);
        const pairs =
            ms.length > 0
                ? ms.map((m) => ({
                    key: jsonPathToFormKey(m.jsonPath ?? ""),
                    value: "mock",
                }))
                : [{key: "", value: ""}];
        return {toolId: tid, pairs, inputPairs: []};
    });
    return {toolBlocks};
}
