-- 记忆条目更新时间（写回 upsert 等场景）
ALTER TABLE lego_memory_items
    ADD COLUMN IF NOT EXISTS updated_at timestamptz;

COMMENT
ON COLUMN lego_memory_items.updated_at IS '内容或元数据更新时写入（如 writeBackOnDuplicate=upsert）';
