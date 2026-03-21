-- 分片：向量化文本与元数据（章节路径等）；全文检索通道（与向量混合召回）
ALTER TABLE lego_kb_chunks
    ADD COLUMN IF NOT EXISTS embedding_text text,
    ADD COLUMN IF NOT EXISTS metadata jsonb NOT NULL DEFAULT '{}'::jsonb;

COMMENT
ON COLUMN lego_kb_chunks.content IS '召回给模型的正文（整节/整块）';
COMMENT
ON COLUMN lego_kb_chunks.embedding_text IS '用于 embedding 的文本；为空表示与 content 相同（兼容旧数据）';
COMMENT
ON COLUMN lego_kb_chunks.metadata IS 'JSON：sectionPath、headingStrategy 等';

-- 生成列：向量通道 + 关键词通道混合检索
ALTER TABLE lego_kb_chunks
    ADD COLUMN IF NOT EXISTS content_tsv tsvector
    GENERATED ALWAYS AS (
    setweight(to_tsvector('simple', coalesce (content, '')), 'A') ||
    setweight(to_tsvector('simple', coalesce (embedding_text, '')), 'B')
    ) STORED;

CREATE INDEX IF NOT EXISTS ix_lego_kb_chunks_content_tsv ON lego_kb_chunks USING gin (content_tsv);
