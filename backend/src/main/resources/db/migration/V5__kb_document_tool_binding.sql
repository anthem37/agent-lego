-- 工具绑定粒度改为「单条知识文档」：一条知识可关联工具，并在正文内引用工具 / 工具出参
ALTER TABLE lego_kb_documents
    ADD COLUMN IF NOT EXISTS linked_tool_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS tool_output_bindings jsonb NOT NULL DEFAULT '{"mappings":[]}'::jsonb;

COMMENT
ON COLUMN lego_kb_documents.linked_tool_ids IS '本条知识关联的工具 ID（JSON 字符串数组）';
COMMENT
ON COLUMN lego_kb_documents.tool_output_bindings IS '正文占位符与工具 JSON 出参映射：{"mappings":[...]}，另支持正文 {{tool:<id>}} 引用工具名';

-- 从集合上移除此前误加的字段（若不存在则跳过）
ALTER TABLE lego_kb_collections
DROP
COLUMN IF EXISTS linked_tool_ids;
ALTER TABLE lego_kb_collections
DROP
COLUMN IF EXISTS tool_output_bindings;
