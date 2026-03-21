-- 知识：冗余存储富文本 HTML；分块/向量化/召回仍以 body（Markdown）为准
ALTER TABLE lego_kb_documents
    ADD COLUMN IF NOT EXISTS body_rich text;

COMMENT ON COLUMN lego_kb_documents.body IS '用于分块、向量化与召回的 Markdown 正文（检索权威文本）';
COMMENT ON COLUMN lego_kb_documents.body_rich IS '可选：富文本 HTML，便于人编辑与详情展示；可为空（仅 Markdown 入库的旧数据）';
