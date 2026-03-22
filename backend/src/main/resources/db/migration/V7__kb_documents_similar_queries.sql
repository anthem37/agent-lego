-- 持久化「相似问」供编辑回显；与向量化时拼入 embedding 的列表一致（JSON 字符串数组）
ALTER TABLE lego_kb_documents
    ADD COLUMN IF NOT EXISTS similar_queries jsonb NOT NULL DEFAULT '[]'::jsonb;

COMMENT
ON COLUMN lego_kb_documents.similar_queries IS '相似问 JSON 数组，与入库/更新时用于 embedding 的列表一致';
