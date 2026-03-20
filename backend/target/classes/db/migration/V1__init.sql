-- Initial schema for platform modules.
-- Embedding columns are JSON placeholders in v1 (upgraded to pgvector in later steps).

create table if not exists platform_models
(
    id
    varchar
(
    32
) primary key,
    provider varchar
(
    64
) not null,
    model_key varchar
(
    128
) not null,
    config jsonb not null default '{}'::jsonb,
    api_key_cipher text null,
    created_at timestamptz not null default now
(
)
    );

create unique index if not exists ux_platform_models_provider_model_key
    on platform_models(provider, model_key);

create table if not exists platform_tools
(
    id
    varchar
(
    32
) primary key,
    tool_type varchar
(
    32
) not null, -- LOCAL | MCP
    name text not null,
    definition jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now
(
)
    );

create unique index if not exists ux_platform_tools_tool_type_name
    on platform_tools(tool_type, name);

create table if not exists platform_agents
(
    id
    varchar
(
    32
) primary key,
    name text not null,
    system_prompt text not null,
    tool_ids text[] not null default '{}'::text[],
    memory_policy jsonb not null default '{}'::jsonb,
    knowledge_base_policy jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now
(
)
    );

create table if not exists platform_workflows
(
    id
    varchar
(
    32
) primary key,
    name text not null,
    definition jsonb not null,
    created_at timestamptz not null default now
(
)
    );

create unique index if not exists ux_platform_workflows_name on platform_workflows(name);

create table if not exists platform_workflow_runs
(
    id
    varchar
(
    32
) primary key,
    workflow_id varchar
(
    32
) not null references platform_workflows
(
    id
) on delete cascade,
    status varchar
(
    32
) not null, -- PENDING | RUNNING | SUCCEEDED | FAILED
    idempotency_key text unique,
    input jsonb not null default '{}'::jsonb,
    output jsonb not null default '{}'::jsonb,
    error text null,
    started_at timestamptz null,
    finished_at timestamptz null,
    created_at timestamptz not null default now
(
)
    );

create index if not exists ix_platform_workflow_runs_workflow_id on platform_workflow_runs(workflow_id);

create table if not exists platform_evaluations
(
    id
    varchar
(
    32
) primary key,
    agent_id varchar
(
    32
) not null references platform_agents
(
    id
) on delete cascade,
    name text not null,
    config jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now
(
)
    );

create unique index if not exists ux_platform_evaluations_agent_id_name
    on platform_evaluations(agent_id, name);

create table if not exists platform_evaluation_runs
(
    id
    varchar
(
    32
) primary key,
    evaluation_id varchar
(
    32
) not null references platform_evaluations
(
    id
) on delete cascade,
    status varchar
(
    32
) not null, -- PENDING | RUNNING | SUCCEEDED | FAILED
    input jsonb not null default '{}'::jsonb,
    metrics jsonb not null default '{}'::jsonb,
    trace jsonb not null default '{}'::jsonb,
    error text null,
    started_at timestamptz null,
    finished_at timestamptz null,
    created_at timestamptz not null default now
(
)
    );

create index if not exists ix_platform_evaluation_runs_evaluation_id on platform_evaluation_runs(evaluation_id);

-- Memory & knowledge base (v1: embeddings stored as JSON placeholders).
create table if not exists memory_items
(
    id
    varchar
(
    32
) primary key,
    owner_scope text not null,
    content text not null,
    metadata jsonb not null default '{}'::jsonb,
    embedding jsonb null,
    created_at timestamptz not null default now
(
)
    );

create index if not exists ix_memory_items_owner_scope_created_at on memory_items(owner_scope, created_at desc);

create table if not exists kb_documents
(
    id
    varchar
(
    32
) primary key,
    kb_key text not null unique,
    name text not null,
    created_at timestamptz not null default now
(
)
    );

create table if not exists kb_chunks
(
    id
    varchar
(
    32
) primary key,
    document_id varchar
(
    32
) not null references kb_documents
(
    id
) on delete cascade,
    chunk_index int not null,
    content text not null,
    metadata jsonb not null default '{}'::jsonb,
    embedding jsonb null,
    created_at timestamptz not null default now
(
),
    unique
(
    document_id,
    chunk_index
)
    );

create index if not exists ix_kb_chunks_document_id on kb_chunks(document_id);

