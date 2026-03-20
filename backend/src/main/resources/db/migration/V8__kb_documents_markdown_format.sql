-- 正式支持 markdown | html；历史 plain 视为 Markdown 源码
update kb_documents
set content_format = 'markdown'
where content_format is null
   or lower(trim(content_format)) = 'plain';

alter table kb_documents
    alter column content_format set default 'markdown';

comment on column kb_documents.content_rich is '知识全文：markdown 时为 MD 源码，html 时为富文本源码';
comment on column kb_documents.content_format is 'markdown | html';
