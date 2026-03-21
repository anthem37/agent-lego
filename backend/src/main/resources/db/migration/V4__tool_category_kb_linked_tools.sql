-- 工具语义分类（与 tool_type 执行方式正交）：QUERY 查询 / ACTION 操作
ALTER TABLE lego_tools
    ADD COLUMN IF NOT EXISTS tool_category varchar (32) NOT NULL DEFAULT 'ACTION';

COMMENT
ON COLUMN lego_tools.tool_category IS '语义分类：QUERY 查询类（只读） / ACTION 操作类（默认）';
