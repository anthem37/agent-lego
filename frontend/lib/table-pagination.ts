import type {TablePaginationConfig} from "antd/es/table";

/**
 * 表格分页中文友好文案（配合根布局 ConfigProvider zhCN，避免「10/page」等英文）。
 */
export function tablePaginationFriendly(overrides?: TablePaginationConfig): TablePaginationConfig {
    return {
        pageSize: 10,
        showSizeChanger: true,
        showQuickJumper: true,
        showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
        ...overrides,
    };
}
