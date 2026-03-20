-- 知识库经典模型：同一 kb_key（集合/空间）下允许多篇文档；原 kb_documents.kb_key UNIQUE 导致只能存一条，已移除。
alter table kb_documents
    drop constraint if exists kb_documents_kb_key_key;

create index if not exists ix_kb_documents_kb_key_created_at on kb_documents (kb_key, created_at desc);
