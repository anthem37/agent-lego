-- Add model base URL support and default model binding on agents.

alter table if exists platform_models
    add column if not exists base_url text null;

alter table if exists platform_agents
    add column if not exists model_id varchar (32) null;

do
$$
begin
        if
not exists (select 1
                       from pg_constraint
                       where conname = 'fk_platform_agents_model_id') then
alter table platform_agents
    add constraint fk_platform_agents_model_id
        foreign key (model_id) references platform_models (id);
end if;
end;
$$;

create index if not exists ix_platform_agents_model_id on platform_agents (model_id);

