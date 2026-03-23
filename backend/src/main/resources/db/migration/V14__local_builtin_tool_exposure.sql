-- 扫描到的内置 LOCAL 工具暴露策略：MCP 对外列表、管理端下拉等由用户配置（默认均开启）
CREATE TABLE lego_local_builtin_tool_exposure
(
    tool_name  varchar(128) PRIMARY KEY,
    expose_mcp boolean     NOT NULL DEFAULT true,
    show_in_ui boolean     NOT NULL DEFAULT true,
    updated_at timestamptz NOT NULL DEFAULT now()
);

COMMENT
ON TABLE lego_local_builtin_tool_exposure IS '与 LocalBuiltinToolCatalog 已注册内置的 @Tool 名对齐；新工具启动时自动插入默认行';
