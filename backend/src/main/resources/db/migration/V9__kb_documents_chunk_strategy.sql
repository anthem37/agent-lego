-- 每条知识独立记录入库时使用的分片策略
alter table kb_documents
    add column if not exists chunk_strategy text not null default 'fixed';

comment
on column kb_documents.chunk_strategy is 'fixed | paragraph | hybrid | markdown_sections';
