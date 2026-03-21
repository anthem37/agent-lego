-- 工具名唯一性以全平台 lower(name) 为准（ux_lego_tools_name_lower），与业务层一致。
-- 删除 V1 中 UNIQUE (tool_type, name)：在已存在 ux_lego_tools_name_lower 时该约束冗余，
-- 且会误导「仅同类型内唯一」的理解。
ALTER TABLE lego_tools
DROP
CONSTRAINT IF EXISTS lego_tools_tool_type_name_key;
