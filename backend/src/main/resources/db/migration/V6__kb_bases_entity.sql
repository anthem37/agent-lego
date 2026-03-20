-- 知识库（空间配置）与 知识（文档/分片）分离：kb_bases 为独立实体；kb_documents 通过 base_id 归属。
create table if not exists kb_bases
(
    id
    varchar
(
    32
) primary key,
    kb_key text not null unique,
    name text not null,
    description text,
    created_at timestamptz not null default now
(
)
    );

-- 从现有文档推断已有空间（迁移期 id 使用 md5(kb_key)，与新库 Snowflake id 共存）
insert into kb_bases (id, kb_key, name, description)
select md5(kb_key), kb_key, kb_key, null
from (select distinct kb_key from kb_documents) t
where not exists (select 1 from kb_bases b where b.kb_key = t.kb_key);

alter table kb_documents
    add column if not exists base_id varchar (32);

update kb_documents
set base_id = md5(kb_key)
where base_id is null
  and kb_key is not null;

alter table kb_documents
    alter column base_id set not null;

alter table kb_documents
    add constraint fk_kb_documents_base foreign key (base_id) references kb_bases (id) on delete cascade;

drop index if exists ix_kb_documents_kb_key_created_at;

alter table kb_documents
drop
column if exists kb_key;

create index if not exists ix_kb_documents_base_created_at on kb_documents (base_id, created_at desc);
