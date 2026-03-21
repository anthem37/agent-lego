/**
 * 知识库富文本内嵌标签的展示文案（编辑器/详情可读性）。
 */

/** 点分路径拆成段（去掉可选的 `$.` 前缀） */
export function kbFieldPathSegments(fieldDotPath: string): string[] {
    return String(fieldDotPath ?? "")
        .trim()
        .replace(/^\$\.?/, "")
        .split(".")
        .map((s) => s.trim())
        .filter(Boolean);
}

/**
 * 富文本里「出参字段」标签的**可见文案**：只显示最后一级字段名（如 data.orderNo → orderNo）。
 * 完整路径请用 {@link formatKbToolFieldPathForTitle} 放在 title 上。
 */
export function formatKbToolFieldPathDisplay(fieldDotPath: string): string {
    const segs = kbFieldPathSegments(fieldDotPath);
    if (segs.length === 0) {
        return "";
    }
    return segs[segs.length - 1]!;
}

/** 悬停/说明用：data.orderNo → data › orderNo */
export function formatKbToolFieldPathForTitle(fieldDotPath: string): string {
    return kbFieldPathSegments(fieldDotPath).join(" › ");
}

/**
 * 出参字段标签：`工具展示名.字段说明`（出参表「说明」列，即 paramDescription）。
 * 无说明时后缀为路径最后一级（兼容旧数据）。
 */
export function formatKbToolFieldChipDisplay(
    toolDisplayName: string,
    fieldDotPath: string,
    fieldDescription?: string | null,
): string {
    const tool = String(toolDisplayName ?? "").trim() || "工具";
    const desc = String(fieldDescription ?? "").trim();
    if (desc) {
        return `${tool}.${desc}`;
    }
    const fieldLeaf = formatKbToolFieldPathDisplay(fieldDotPath);
    return fieldLeaf ? `${tool}.${fieldLeaf}` : tool;
}
