-- 工具管理：展示名（中文名）与平台侧说明，与运行时 name、definition 并存
ALTER TABLE lego_tools
    ADD COLUMN IF NOT EXISTS display_label varchar (256),
    ADD COLUMN IF NOT EXISTS description text;

COMMENT
ON COLUMN lego_tools.display_label IS '展示名/中文名（可选）；运行时仍以 name 为键';
COMMENT
ON COLUMN lego_tools.description IS '平台侧工具说明（给人读）；模型侧说明仍可放在 definition.description';
