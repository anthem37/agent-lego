-- 知识库集合级分片策略（创建集合时指定，入库时按策略切分后再 embedding）
ALTER TABLE lego_kb_collections
    ADD COLUMN IF NOT EXISTS chunk_strategy varchar (32) NOT NULL DEFAULT 'FIXED_WINDOW',
    ADD COLUMN IF NOT EXISTS chunk_params jsonb NOT NULL DEFAULT '{"maxChars": 900, "overlap": 120}'::jsonb;

COMMENT
ON COLUMN lego_kb_collections.chunk_strategy IS 'FIXED_WINDOW | PARAGRAPH | HEADING_SECTION';
COMMENT
ON COLUMN lego_kb_collections.chunk_params IS 'JSON：maxChars、overlap；HEADING_SECTION 另有 headingLevel、leadMaxChars';
