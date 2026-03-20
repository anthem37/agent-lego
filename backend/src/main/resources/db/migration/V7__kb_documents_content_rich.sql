-- 知识正文：保存用户提交的完整内容（纯文本或 HTML）；检索分片仍由应用层从纯文本切分。
alter table kb_documents
    add column if not exists content_rich text;

alter table kb_documents
    add column if not exists content_format text not null default 'plain';

comment
on column kb_documents.content_rich is '知识全文：plain 时为纯文本，html 时为富文本源码';
comment
on column kb_documents.content_format is 'plain | html';
